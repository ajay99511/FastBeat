<!--
  This text is the DEFAULT PR DESCRIPTION. Edit it; do not delete it wholesale.
  Delete only the sections that genuinely do not apply, and say why in one line.

  Keep it honest. The one rule the playbook will not bend on: "evidence, not
  assertion". A claim with no command behind it is worth less than an admission.
-->

## What this changes

<!-- One or two sentences. What the user or the next engineer can now do that
     they could not before, or what is no longer broken. Not a file list. -->

Closes #

## Why

<!-- The problem, not the patch. If this is non-obvious or was a bug, say what
     the incorrect behaviour was and what caused it. -->

## Type of change

- [ ] `fix` — bug fix (triggers a **patch** tag)
- [ ] `feat` — new feature (triggers a **minor** tag)
- [ ] `perf` — performance (triggers a **patch** tag)
- [ ] `refactor` / `test` / `docs` / `style` / `chore` — no release
- [ ] **Breaking change** (`feat!:` or a `BREAKING CHANGE:` footer → **major** tag)

<!-- WHY THIS BOX MATTERS, and is not bureaucracy: release.yml computes the next
     version tag by grepping the NON-MERGE commit subjects in this branch -- not
     the PR title, because PRs here land as merge commits. So the prefixes on the
     commits inside this branch are what actually move the version. If the box
     above and your commit subjects disagree, the commits win. Fix the commits.
     versionCode/versionName are derived from git; never hand-edit them. -->

## Verification

Paste the commands you ran and what they said. "Tests pass" is not evidence.

```text
./gradlew assembleDebug lint testDebugUnitTest detekt ktlintCheck --stacktrace
<paste the result>
```

- [ ] The full local gate above passes (same tasks CI runs — README § Pre-Push Checklist)
- [ ] New or changed behaviour has a test that would **fail without this change**

**Not verified:** <!-- Required if anything is untested. Be specific: "no device
test on API 26", "migration tested forward only". An empty answer here reads as
a claim of full coverage. -->

## Manual check on a device

<!-- WHY THIS SECTION EXISTS: CI runs NO instrumented tests -- there is no
     emulator job. A green check proves the project compiles, the unit suite
     passes, R8 succeeds, and style is clean. It proves NOTHING about playback,
     Room migrations, the Hilt graph at startup, or Compose rendering. If this PR
     touches any of those, CI cannot cover you and a device run is the only gate. -->

- [ ] Not needed — this PR touches none of playback, persistence, DI or UI
- [ ] Ran on a device/emulator: **API level ___ , device ___**

What I exercised by hand:

## Risk checklist

Tick only what applies; strike through the rest.

- [ ] **Room schema changed** → ships a `Migration` **and** a migration test.
      `fallbackToDestructiveMigration()` is banned: this app is offline, so a
      dropped table is a user's playlists and history gone with no server copy.
- [ ] **New files added** → `git status --short` is clean and `git ls-files <paths>`
      lists every one of them. <!-- A bare `/app` rule in .gitignore once kept two
      source files out of the repo and merged a master that did not compile
      (PR #10). .gitignore carries the warning; this line is the habit. -->
- [ ] **UI changed** → screenshots or a screen recording below (light **and** dark).
- [ ] **New async surface** → renders all four states: loading, empty, error with
      a retry, and success.
- [ ] **Accessibility** → icon-only controls name their action in
      `contentDescription`, decorative icons pass `null`, touch targets ≥ 48dp.
- [ ] **Permissions, exported components, `FileProvider` or URI handling changed**
      → called out explicitly here, so it gets a security read. See SECURITY.md § Scope.
- [ ] **New dependency** → why this one, its size cost, and its licence.
- [ ] No empty `catch` blocks, no `!!`, no `GlobalScope`, no `collectAsState()`
      (use `collectAsStateWithLifecycle()`), no hardcoded dispatchers.

## Screenshots / recording

<!-- Light and dark. Before and after if this is a visual fix. Delete if N/A. -->

## Anything else a reviewer should know

<!-- Known gaps, follow-ups you deliberately left out of scope, decisions you are
     unsure about, or the one file you want someone to look at hardest. -->

---

<!-- By opening this PR you agree your contribution is licensed under Apache-2.0
     (LICENSE) and that you will follow CODE_OF_CONDUCT.md. -->
