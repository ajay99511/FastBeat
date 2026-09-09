# 🧭 FastBeat Kotlin & Android Engineering Playbook

> **Audience:** anyone writing Kotlin in this repository — contributors, reviewers, and the future
> version of you who has forgotten why a rule exists.
> **Status:** living document. It describes the bar we hold code to, not an aspiration.
> **Companion documents:** [ENGINEERING_AUDIT.md](./ENGINEERING_AUDIT.md) (what was wrong on
> 2026-08-21), [AUDIT_ADDENDUM.md](./AUDIT_ADDENDUM.md) (verification pass),
> [../implementation_plan.md](../implementation_plan.md) (the remediation being executed).

The audit told us where we stood. This playbook says where the line is from now on. Where the two
disagree, this document wins — it is the forward-looking standard.

---

## 0. How much rigor does this change deserve?

The most common failure in "enterprise quality" is not too little process, it is **uniform**
process: a typo fix carrying the same ceremony as a schema migration. Rigor scales with
**blast radius × how hard it is to undo**. Classify every change before starting, and say the tier
out loud in the PR description.

| Tier | FastBeat examples | What it earns |
|------|-------------------|---------------|
| **Trivial** | Copy tweak, log level, adding a `@Preview` | Follow conventions, run `ktlintFormat`. No plan, no design note. |
| **Standard** | New screen, new sort option, bug fix in a ViewModel | Tests at the right level, self-review, `detekt` + unit tests green |
| **Consequential** | Room migration, DataStore schema change, new dependency, changing `QueuePolicy`, permission changes | Written design note, migration **and** rollback path, migration test on device, staged behind a flag or a version check |
| **Foundational** | Swapping Media3 for another engine, changing the DI graph shape, new module, changing the persisted queue format | All of the above, plus written alternatives-considered and an explicit exit strategy |

**Misclassifying downward is the expensive error.** Anything that touches persisted user data —
the Room database, the DataStore session, the saved queue — is **never** Trivial, no matter how
small the diff looks.

---

## 1. Non-negotiables

These hold regardless of deadline. Each one is either irreversible or silently compounding.

1. **User data correctness beats everything.** A user's playlists, play history, streaks, and saved
   queue are irreplaceable — this app is offline, so there is no server backup to restore from. Never
   ship a change that can lose them. `fallbackToDestructiveMigration()` is banned.
2. **No silent failure.** An empty `catch` is a defect. Catching broadly at a platform boundary is
   allowed (see §4.4) — catching and doing nothing is not. Detekt's `SwallowedException` rule is
   active and it is not there for decoration.
3. **Evidence, not assertion.** "Tested" and "works" are claims that require a command and its
   output. Paste the result. Never claim verification you did not run — and when a verification is
   genuinely impractical, state the limit plainly rather than implying coverage you don't have.
4. **Reversibility.** Anything shipped can be turned off — a revert, a flag, or a documented
   rollback. Migrations are reversible, or explicitly documented as not.
5. **The codebase stays coherent.** Match local conventions even where you prefer others. Two
   dialects cost more than either dialect saves. If a convention is genuinely wrong, change it
   everywhere in a dedicated commit — don't start a second dialect inside a feature PR.
6. **Accessibility is a requirement, not a polish item.** See §7.

---

## 2. Kotlin language practices

### 2.1 Nullability is a design decision, not an accident

```kotlin
// ❌ Nullable because we didn't think about it
data class Track(val album: String?, val year: Int?)

// ✅ Nullable because "unknown" is a real, meaningful state for MediaStore metadata,
//    and callers are forced to decide what to render for it
data class Track(
    /** `null` when MediaStore has no album tag — render as "Unknown album", never as empty. */
    val album: String?,
    val year: Int?,
)
```

- `!!` is a bug report waiting to happen. The only acceptable use is after a `check`/`require` that
  just proved non-nullness, and even then prefer `?: error("...")`, which names the invariant.
- Prefer `?:` with a *meaningful* default over `?: return`, which silently drops work. If dropping is
  correct, say why in a comment.
- Platform types from Android APIs (MediaStore cursors, `Intent` extras) arrive as nullable. Convert
  them to your own non-null types **at the boundary**, once.

### 2.2 Immutability by default

- `val` unless mutation is required. A `var` in a class body is a state machine — name the states.
- Expose read-only types outward: `List`, `Map`, `Set`, `StateFlow` — never `MutableList` or
  `MutableStateFlow` from a public API.

```kotlin
// ✅ The standard ViewModel shape in this codebase
private val _uiState = MutableStateFlow(LibraryUiState())
val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
```

- Use `copy()` on data classes for state transitions. Never mutate a state object in place — Compose
  compares by equality and will not recompose if you mutated what it already holds.

### 2.3 Model the domain with the type system

Illegal states should be unrepresentable. A sealed hierarchy that makes `when` exhaustive is worth
more than any amount of defensive checking.

```kotlin
// ✅ See model/PlayerStates.kt and model/AppError.kt for the established pattern
sealed interface AppError {
    data object MediaAccessDenied : AppError
    data object DeleteFailed : AppError
    data class Playback(val cause: PlaybackFailure) : AppError
}
```

- **Boolean pairs are a smell.** `isLoading: Boolean, error: String?` permits "loading and failed
  simultaneously". A sealed state class does not.
- Use `@JvmInline value class` for identifiers that must not be swapped: a `PlaylistId` and a
  `TrackId` are both `Long`, and the compiler should stop you mixing them.
- Never `when` on a sealed type with an `else` branch. The exhaustiveness error when someone adds a
  case is the entire point — `else` throws it away.

### 2.4 Functions

- One thing, one level of abstraction. A function that both decides *and* performs I/O is two
  functions.
- Expression bodies for genuine one-liners only. If it needs a `?:` chain and a `let`, use a block.
- Extension functions for adapting types you don't own; not as a place to hide business rules.
- Default arguments beat overloads. Use named arguments at call sites with more than two parameters —
  especially for adjacent booleans, which are unreadable positionally.

```kotlin
// ❌ What are these?
queueManager.enqueue(track, true, false)

// ✅
queueManager.enqueue(track, playNext = true, preserveShuffle = false)
```

### 2.5 Errors and exceptions

- `require()` for caller mistakes (invalid arguments), `check()` for internal invariants, `error()`
  for unreachable states. All three produce a message you can read in a crash report;
  `IllegalStateException()` with no message does not.
- **Expected failures are return values, not exceptions.** A missing media file is expected — it
  belongs in `AppError`, not in a `throw`. Reserve exceptions for programming errors and genuinely
  exceptional platform failures.
- Never catch `Exception` in domain or ViewModel code. At platform boundaries where the framework's
  thrown types are undocumented (`AudioEffect`, `MediaMetadataRetriever`, PiP entry) a broad catch is
  permitted **and must log with enough context to diagnose it**. This is exactly why
  `TooGenericExceptionCaught` is disabled in `detekt.yml` while `SwallowedException` stays on.

### 2.6 Coroutines and structured concurrency

- `GlobalScope` is banned. Every coroutine has an owner whose cancellation is meaningful:
  `viewModelScope`, a `LifecycleOwner` scope, or a scope the service owns and cancels.
- **Inject dispatchers, never hardcode them.** A hardcoded `Dispatchers.IO` makes a function
  impossible to test deterministically.

```kotlin
class MediaRepository @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun scanLibrary(): List<MediaFile> = withContext(io) { /* ... */ }
}
```

- **Suspend functions are main-safe.** A `suspend fun` must be callable from the main dispatcher
  without blocking it — it switches internally. Callers should never need to know.
- `SupervisorJob` when sibling failures must not cascade; a plain `Job` when they must. Pick
  deliberately rather than copying whichever you saw last.
- Cancellation is cooperative: never swallow `CancellationException`. If you `catch (e: Exception)`
  around a suspend call, rethrow `CancellationException` explicitly.

### 2.7 Flow discipline

- **Cold flows for data, hot state for UI.** The repository returns a `Flow`; the ViewModel converts
  with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. That 5-second
  timeout is what survives a configuration change without re-querying the library.
- In Compose, always `collectAsStateWithLifecycle()` — never `collectAsState()`. The latter keeps
  collecting while the app is backgrounded, which burns battery and is a genuinely user-visible cost
  on a media app.
- `distinctUntilChanged()` before anything expensive. `flatMapLatest` for search-as-you-type, so a
  slow query cannot land after a newer one.
- Don't nest `collect` inside `collect`. Use `combine`, `zip`, or `flatMapLatest`.

---

## 3. Architecture

### 3.1 The layering, and what each layer may know

```
UI (Compose)  →  ViewModel  →  UseCase (domain)  →  Repository  →  Room / MediaStore / DataStore
```

| Layer | May depend on | Must never contain |
|-------|---------------|--------------------|
| `ui/` | ViewModels, models | Business rules, I/O, `Context` beyond what Compose provides |
| `viewmodel/` | Use cases, repositories, models | Compose types, `View`, `Activity` references |
| `domain/` | Repository interfaces, models | Android framework types — this is the layer that stays pure and trivially testable |
| `repository/`, `data/` | DAOs, framework APIs | UI state types |

The `domain/` layer is the newest and the thinnest — `CalculateStreakUseCase`,
`GetContinueWatchingUseCase`, `LogPlayEventUseCase`. **Rules with real logic belong there**, because
that is where they can be tested without a device, an emulator, or a mocked ViewModel.

### 3.2 What earns a use case, and what does not

Not every call needs one. A use case earns its existence when it (a) contains a decision, (b) spans
more than one repository, or (c) is used from more than one place. A pass-through use case that just
forwards to a DAO is indirection with no payoff — delete it and call the repository.

### 3.3 Keep classes from becoming god objects

`PlaybackViewModel` was 2,297 lines with 65 public methods and 28 `MutableStateFlow`s. That is the
single most expensive defect this codebase has had, and the decomposition into `QueueManager`,
`MediaControllerBinder`, `PlaybackAnalyticsTracker`, `MediaDeletionHandler` and `BookmarkManager`
exists so it does not happen again.

**Warning signs to act on before review, not after:**

- The class name contains "Manager", "Helper" or "Util" *and* you cannot state its one
  responsibility in a single sentence without the word "and"
- More than roughly eight constructor dependencies
- A test needs more than three fakes to construct the subject

### 3.4 Dependency injection (Hilt)

- Constructor injection everywhere. Field injection only where the framework constructs the object:
  `Activity`, `Service`, `BroadcastReceiver`.
- **Bind interfaces, not implementations**, wherever a fake would be useful in tests. `@Binds` an
  interface so a test can substitute a fake without a mocking framework.
- Scope deliberately. `@Singleton` for genuinely process-wide state (the `MediaController`
  connection, DataStore). Everything else is `@ViewModelScoped` or unscoped. An over-scoped object is
  a memory leak that outlives every screen that used it.
- Constructing a dependency inline (`= MediaRepository(context)`) inside a class Hilt already manages
  defeats the graph and makes the class untestable. Don't.

---

## 4. Android platform practices

### 4.1 Compose

- **State goes down, events go up.** Composables take data and lambdas. A composable that takes a
  ViewModel is acceptable only at the screen root, and even there prefer hoisting so the body stays
  previewable.
- Every composable rendering async data handles **four** states explicitly: loading, empty, error
  (with a retry affordance), and success. Missing empty and error states is the most common Compose
  review finding anywhere, this repository included.
- **Stability drives performance.** An unstable parameter forces recomposition every time the parent
  recomposes. Prefer stable data classes or immutable collections over raw `List` in hot list items.
  `@Immutable` and `@Stable` are promises the compiler cannot verify — only annotate what genuinely
  is.
- Side effects use the right API: `LaunchedEffect` for suspending work keyed to state,
  `DisposableEffect` for anything needing cleanup, `rememberUpdatedState` when a long-lived effect
  must see the latest lambda, `SideEffect` for publishing to non-Compose code. Never launch work
  directly in a composable body.
- `derivedStateOf` when a value is computed from state but changes less often than its inputs — for
  example "is the list scrolled past the first item". Without it you recompose on every scrolled
  pixel.
- `key()` in lazy lists, always, on a stable identity. Without it, deleting an item animates the
  wrong row and the scroll position jumps.
- No business rules in composables. A `@Composable` containing a sorting comparator or a shuffle
  decision has put that logic somewhere it cannot be unit tested — move it to a ViewModel or a use
  case.

### 4.2 Lifecycle and process death

Android kills your process. The state that survives is exactly the state you persisted.

- **Configuration change** → the `ViewModel` survives.
- **Process death** → only `SavedStateHandle`, DataStore and Room survive. `remember` and plain
  ViewModel fields do not.
- Decide explicitly, per piece of state, which of three kinds it is: transient (recomputable),
  UI-restore (`SavedStateHandle`), or durable (DataStore/Room). The saved queue is durable, which is
  why it lives in `QueuePersistence` and not in a field.

### 4.3 Permissions and scoped storage

- Request in context, at the moment the capability is needed, with an explanation of why — never as
  a wall at launch.
- **Any permission can be revoked at any time, including while the app is backgrounded.** Handle
  denial as a normal path with a working degraded UI, not as an error screen.
- Scoped storage (Android 11+) routes delete and rename through MediaStore consent intents. Partial
  failure is normal — hence `msg_delete_partially_failed`. Report exactly what happened, not a
  generic failure.

### 4.4 Platform boundaries

`AudioEffect`, `MediaMetadataRetriever`, PiP entry and audio focus all throw undocumented types on
some OEM builds. At these boundaries: catch broadly, log with context, degrade gracefully. Everywhere
else, catch narrowly. The `eq_unavailable` string exists because the equalizer legitimately does not
exist on some devices, and the right response is an explanation rather than a crash.

### 4.5 Battery, data and thermals are user-visible

This is a media player — it runs for hours with the screen off. Poll nothing. Batch writes. Stop
collecting flows the user cannot see. `WhileSubscribed(5_000)` and `collectAsStateWithLifecycle()`
are battery decisions as much as correctness decisions.

---

## 5. Data and persistence

- **Migrations are versioned and tested.** Every schema change ships a `Migration` plus a case in
  `app/src/androidTest/.../db/MigrationTest.kt`. Room's exported schema JSON is committed — it is the
  record of what actually shipped, and a migration test without it proves nothing.
- `fallbackToDestructiveMigration()` deletes user data on upgrade. It is banned in every build type,
  including debug — debug is precisely where you would otherwise stop noticing.
- For anything holding real user data, migrate **expand → backfill → contract**: add the new column,
  populate it, then stop reading the old one, across separate releases. A rename in one step means an
  interrupted upgrade loses data.
- DataStore over `SharedPreferences` for new work — async, transactional, typed.
- Serialization is `kotlinx.serialization`. A persisted `@Serializable` class is a **schema**: new
  fields need defaults, removed fields need a deprecation window, and a field rename is a breaking
  change requiring a version bump and a reader that handles both shapes.
- Old app versions live on devices indefinitely. A persisted format must be readable by the code that
  wrote it **and** by the code that will read it two releases from now.

---

## 6. Testing

### 6.1 What deserves which kind of test

Current state: 26 JVM unit test suites, 2 instrumented. The shape is right — most logic should be
provable without a device — but the instrumented tier is thin, and that gap is known rather than
hidden.

| Test type | Use it for | Cost |
|-----------|-----------|------|
| **JVM unit** (Kotest + MockK + Turbine) | Domain logic, ViewModel state transitions, queue/shuffle rules, sorting, error mapping | Milliseconds — the default |
| **Robolectric** | Code needing Android types but no real device: `Uri` parsing, resource lookup | Seconds |
| **Instrumented** (`androidTest`) | Room migrations, MediaStore behaviour, real playback | Minutes, plus a device |
| **Compose UI** (semantics) | Screen contracts, accessibility labels, four-state rendering | Seconds to minutes |
| **Macrobenchmark** (`:baselineprofile`) | Startup and scroll journeys | Slow, release-time |

### 6.2 How to write them

- **Test behaviour, not implementation.** A test that breaks when you rename a private method is a
  maintenance tax that catches nothing.
- Name tests as sentences: `` `shuffle off then on preserves the currently playing track` ``.
- **Prefer fakes to mocks.** A `FakeMediaRepository` backed by an in-memory list reads better than
  six `every { }` stanzas, and it doesn't assert on call ordering you don't actually care about.
  MockK is for boundaries you cannot reasonably fake.
- Flows are tested with Turbine, not by collecting into a list and sleeping.
- Coroutines are tested with `runTest` and an injected `TestDispatcher`. If a test needs a real
  delay, the production code has a hidden dispatcher.
- **Every bug fix ships with a test that fails without the fix.** It is the only way to know the fix
  addresses the cause rather than a symptom.
- Deterministic, or delete it. A flaky test is worse than no test: it trains the team to ignore red.

### 6.3 What "done" means

A change is done when all of these are true — not when it compiles:

- [ ] `./gradlew testDebugUnitTest` green, with new tests covering the change
- [ ] `./gradlew detekt ktlintCheck` clean, with **no new baseline entries** — the baseline records
      old debt, it is not a place to hide new debt
- [ ] `./gradlew assembleDebug` green
- [ ] Migration test added and run on a device, if the schema changed
- [ ] Accessibility checked per §7 for any UI change
- [ ] Empty, error and loading states exist for any new async surface
- [ ] The PR states its tier (§0) and, for Consequential and above, the rollback path

---

## 7. Accessibility

Accessibility is a correctness requirement. Roughly one in six people has a disability, and for a
media player specifically, screen-reader and switch-access users are a real and underserved audience.
Target **WCAG 2.2 AA**.

### 7.1 Rules

- **Every interactive element has a label.** `contentDescription` on icon-only controls, describing
  the *action* ("Play", "Add to queue") rather than the picture ("play triangle").
- **`contentDescription = null` is correct and required for decorative images** — the icon inside an
  already-labelled row, the album art beside a track title that reads out anyway. A redundant label
  doubles the length of screen-reader output. The test: if this element vanished, would a
  screen-reader user lose information? If not, `null` is right. Don't "fix" these by adding
  descriptions.
- **Touch targets are at least 48dp.** `Modifier.size(24.dp)` on a clickable icon is a defect; wrap
  it in a 48dp `IconButton` or apply `Modifier.minimumInteractiveComponentSize()`.
- **Group what should be read together.** A row showing "Swipe left or right" beside "Seek" is two
  disconnected announcements by default. `Modifier.semantics(mergeDescendants = true) { }` with an
  explicit `contentDescription` makes it one sentence.
- **Mark headings.** `Modifier.semantics { heading() }` on section titles lets screen-reader users
  jump between sections instead of swiping through every element in between.
- **State is announced, not merely drawn.** A toggled favourite needs `toggleableState` or a
  `stateDescription`; colour alone is invisible to a screen-reader user and to a colour-blind one.
- **Custom gestures need an accessible alternative.** The video player's swipe-to-seek and
  double-tap-to-skip are, on their own, unusable with a screen reader or switch access. Every gesture
  needs an equivalent focusable control or a `customActions` semantics entry.
- **Contrast:** 4.5:1 for body text, 3:1 for large text and UI component boundaries — for every
  user-selectable theme colour, not just the default one.
- **Respect user settings.** Text must survive 200% font scale without truncation: use `sp` for text,
  never `dp`, and never fix a text container's height. Respect reduced-motion for navigation
  transitions.
- **Strings live in `strings.xml`.** Hardcoded UI text cannot be translated, and a screen reader
  feeding English text to a Hindi TTS engine produces gibberish. This is an accessibility rule, not
  only a localisation one.

### 7.2 How to verify

```bash
# Compose semantics assertions — the fast automated tier
./gradlew connectedDebugAndroidTest
```

Then a manual pass for any UI change, in this order. It takes about five minutes:

1. **TalkBack on.** Swipe through the whole screen. Every stop announces something meaningful, in a
   sensible order, with no "unlabeled button".
2. **Font size at maximum** (Settings → Display → Font size). Nothing truncated or overlapping.
3. **Display size at maximum.** The layout still works.
4. **Dark and light theme**, plus at least two custom accent colours.
5. **Switch Access or a keyboard.** Everything reachable, focus visible, no traps.

Automated checks catch missing labels and undersized targets. They cannot tell you the reading order
is nonsense, or that a label is technically present but useless. Do the manual pass.

### 7.3 The in-app guide

`ui/screens/AccessibilityGuideScreen.kt` is the user-facing walkthrough, reachable from
**Me → Accessibility Guide**. It has a second job beyond documenting features: it is the screen
disabled users are most likely to open first, so it must be the most accessible screen in the app.
When you add a feature with a gesture or any non-obvious interaction, update that guide in the same
PR.

---

## 8. Performance

- **Measure before optimising.** The `:baselineprofile` module and Macrobenchmark exist so that "it
  feels faster" can be replaced with a number.
- Startup is the most-noticed metric. Nothing blocking in `Application.onCreate()`; lazy-initialise
  anything not needed for the first frame.
- **Recomposition budget:** use Layout Inspector's recomposition counts. A list item recomposing on
  every scroll frame means an unstable parameter or a missing `key`.
- Never do I/O or heavy computation on the main thread. `StrictMode` in debug builds catches this
  during development rather than in a Play Console ANR report.
- Media libraries reach tens of thousands of files. Anything touching the full library must be paged,
  streamed, or done off the main thread — an `O(n)` scan that is fine with 200 tracks is an ANR at
  40,000.
- The Baseline Profile is a **release-time artifact**, regenerated at release rather than committed.
  See `implementation_plan.md` P5-E for the reasoning.

---

## 9. Security and privacy

FastBeat is fully offline, which removes whole categories of risk — and makes the remaining ones
easier to overlook.

- **The privacy promise is a technical constraint.** No analytics SDK, no crash reporter that phones
  home, no network calls. Adding any dependency that opens a socket is a **Foundational** change and
  needs explicit product sign-off.
- Never log user content: file paths, track titles and playlist names are personal. Log identifiers
  and states, not payloads.
- No secrets in source or in any committed properties file. Signing config comes from the
  environment. `.gitignore` correctness is a security control in its own right — see
  `AUDIT_ADDENDUM.md` for what happened when it was wrong.
- Every new dependency is a supply-chain decision: licence compatibility, maintenance status,
  transitive weight. Dependabot runs on a low-touch cadence with no auto-merge — updates get
  reviewed, not rubber-stamped.
- Exported components (`PlaybackService`, activities) are attack surface. Validate every `Intent`
  extra as untrusted input.
- R8 with real rules for release builds, and verify the release build actually runs — obfuscation
  breaks reflection-based serialization silently.

---

## 10. Tooling and gates

| Gate | Command | Enforces |
|------|---------|----------|
| Format | `./gradlew ktlintFormat` / `ktlintCheck` | Official Kotlin style |
| Static analysis | `./gradlew detekt` | Complexity, swallowed exceptions, naming — configured in `detekt.yml` |
| Android lint | `./gradlew lintDebug` | Platform misuse, accessibility, resource issues |
| Unit tests | `./gradlew testDebugUnitTest` | Logic |
| Instrumented | `./gradlew connectedDebugAndroidTest` | Migrations, UI semantics |
| Build (debug) | `./gradlew assembleDebug` | Compiles |
| Build (release) | `./gradlew assembleRelease` | R8 shrinking and obfuscation hold; signing config resolves |

`detekt-baseline.xml` records **pre-existing** debt so that new code is held to the current bar.
Adding to the baseline to make a new violation pass defeats the tool — fix it, or justify it in
review.

`detekt-baseline.xml` and `app/config/ktlint/baseline.xml` both index violations **by line
number**. Inserting an import shifts every entry below it, so a baselined file can start failing on
debt it already carried. Correct the line numbers when that happens; regenerating the whole
baseline instead would silently absolve everything else in the file too.

### 10.1 The release build is a different build

`isMinifyEnabled` and `isShrinkResources` are on for `release` only. R8 can remove a class that
nothing but reflection reaches, so a release APK can compile, install and then crash on launch
while every debug gate is green. CI therefore builds `assembleRelease` on every push — the release
path is proven continuously rather than the first time somebody cuts a release.

Two rules follow:

- **Do not add blanket `-keep` rules** to `proguard-rules.pro` to make a suspected R8 problem go
  away. Nearly every dependency here ships its own consumer rules. Diagnose against
  `mapping/release/usage.txt` (what R8 removed) and `seeds.txt` (what it kept) first; a speculative
  keep rule defeats shrinking and hides the real cause.
- **Archive `mapping.txt` with every distributed build.** It is not reproducible from a later
  build, and without it a production stack trace is unreadable. `-keepattributes
  SourceFile,LineNumberTable` is what gives it line numbers to decode; the pairing of the two is
  the whole mechanism.

**CI does not run instrumented tests** — there is no emulator in the pipeline. A green build
therefore proves compilation, style, static analysis, JVM unit tests and that R8 succeeds, and says
nothing about on-device behaviour. Know what your green tick actually means before relying on it.

---

## 11. Commits and pull requests

Conventional Commits, as described in [CONTRIBUTING.md](../CONTRIBUTING.md). Beyond the format:

- **The commit message explains why; the diff shows what.** A message restating the diff is wasted
  space.
- One logical change per commit. Formatting churn gets its own commit, or it hides the real change
  from review and from `git bisect`.
- **A PR description saying "see title" is an unreviewed PR.** State the tier, what you verified and
  with which command, what you did *not* verify, and the rollback path for anything Consequential.
- Reviewers: ask about failure modes, data safety, and what happens with a 40,000-file library before
  commenting on style. Style is ktlint's job, and a review spent on formatting is a review that
  missed the migration bug.

---

## 12. Skills that make a strong mobile developer

Everything above is the *what*. This section is the *what you need to know* — the competency map this
project actually exercises. Use it to find gaps, not to grade yourself.

### 12.1 Foundations

**Kotlin, properly.** Not "can read it" — coroutines and structured concurrency, Flow operators and
their back-pressure behaviour, sealed hierarchies and exhaustiveness, delegation, `inline`/`reified`
and why they exist, and a working model of what the compiler does with `suspend`.

**The Android platform, mechanically.** Not the API surface — the model underneath: the activity and
process lifecycle, why your process gets killed and what survives it, the main thread and what 16ms
actually means, how the input system delivers a touch, how Binder IPC is what makes `MediaSession`
work across processes.

**Concurrency for real.** Race conditions, atomicity, cancellation, and why "it works on my Pixel"
means nothing on a mid-tier device where the timing is different.

### 12.2 The craft

**UI as a function of state.** Compose's mental model — recomposition, stability, side effects, state
hoisting — is the difference between UI that scales and UI that becomes unmaintainable at twenty
screens.

**Architecture judgement, in both directions.** Knowing MVVM or MVI is table stakes. The senior skill
is knowing when a layer earns its keep and when it is ceremony — recognising both the god object and
the six-file indirection chain needed to read one boolean, and treating both as defects.

**Testing as design feedback.** Hard-to-test code is badly factored code; the difficulty is the
signal. Knowing which tier a given risk belongs in — and being willing to *not* write a test that
would only assert the framework works.

**Performance as measurement.** Profiler, Layout Inspector, Macrobenchmark, `StrictMode`, systrace.
Never "I optimised it", always "340ms to 180ms, here is the trace".

**Data and persistence.** Schema design, migrations, and the discipline that comes from knowing an
upgrade bug means users lose data nobody can get back.

### 12.3 What separates senior from strong-mid

**Blast-radius thinking.** Instinctively asking "what breaks if this is wrong, and how would I know?"
before writing code — then calibrating effort to the answer, instead of applying maximum rigor
uniformly.

**Reading the fence before removing it.** Assuming odd-looking code had a reason, finding the reason,
and *then* deciding. Most cleanups that cause incidents skipped that step.

**Backward compatibility as a permanent condition.** Old versions of your app run forever. Every
persisted format and every contract is a promise you cannot retract with a hotfix.

**Device reality.** Your test device is the top 5%. Real users have three-year-old mid-tier phones,
2GB of free storage, aggressive OEM battery killers, 40,000 photos, and TalkBack switched on.

**Writing things down.** The decision record, the "why" comment, the honest verification note that
says what you did *not* prove. Teams move at the speed of their shared understanding, and
undocumented decisions get re-litigated every six months.

**Calibrated honesty about your own work.** "I verified A and B, but not C, and here is why C is
hard" is worth more than a confident green tick. It is the habit that separates engineers you can
trust unsupervised from engineers you cannot.

### 12.4 Where this codebase will teach you

| Skill | Read this |
|-------|-----------|
| Coroutines and Flow in anger | `viewmodel/PlaybackViewModel.kt`, `playback/QueueManager.kt` |
| Cross-process media | `service/PlaybackService.kt`, `playback/MediaControllerBinder.kt` |
| State that survives process death | `playback/QueuePersistence.kt`, `data/AppPreferencesManager.kt` |
| Room migrations done properly | `data/db/`, `app/src/androidTest/.../MigrationTest.kt` |
| Adaptive layouts | `ui/adaptive/` |
| Testing without a device | `app/src/test/` |
| What "not good enough" looked like | [ENGINEERING_AUDIT.md](./ENGINEERING_AUDIT.md) |

---

## 13. When this document is wrong

It will be. A rule here that keeps costing more than it saves is a bug in the playbook.

Open a PR that changes the rule and says why, with the case that exposed it. What is not acceptable
is silently ignoring a rule — that is how a codebase ends up with two dialects and a document nobody
trusts.
