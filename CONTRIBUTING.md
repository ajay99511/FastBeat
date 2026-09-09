# 🤝 Contributing to FastBeat

Thank you for considering contributing to FastBeat! This document provides guidelines to help you through the process.

---

## 🌟 How to Contribute

### Types of Contributions We Welcome

| Type | Description | Examples |
|------|-------------|----------|
| **🐛 Bug Reports** | Report repeatable bugs | Crash reports, seek bar issues, UI glitches |
| **💡 Feature Requests** | Suggest new improvements | Lyrics support, Chromecast, equalizer |
| **📝 Documentation** | Improve documentation | Fixing typos, adding code examples, improving guides |
| **🔧 Code Contributions** | Fix bugs or add features | Playback engine fixes, UI components, data layer |
| **🎨 Design** | UI/UX improvements | Custom themes, animations, icons |
| **🧪 Testing** | Add more tests | Unit tests for Repositories/ViewModels, UI tests |

---

## 🚀 Quick Start for Contributors

### 1. Fork the Repository

```bash
# Click "Fork" on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/FastBeat.git
cd FastBeat
```

### 2. Create a Branch

```bash
# Always branch from main
git checkout main
git pull origin main

# Create a feature branch
git checkout -b feature/your-feature-name
```

### 3. Make Your Changes
- Ensure your code follows the existing style and architecture (MVVM).
- Keep changes modular and well-documented.

### 4. Test Your Changes

```bash
# Run all Unit Tests
./gradlew test

# Run all UI Tests
./gradlew connectedAndroidTest
```

### 5. Commit Your Changes

```bash
# Stage changes
git add .

# Commit using Conventional Commit format
git commit -m "feat: add support for local lyrics"
```

### 6. Push and Create PR

```bash
# Push to your fork
git push origin feature/your-feature-name

# Go to GitHub and open a Pull Request
```

---

## 📝 Coding Guidelines

> **The full standard lives in [docs/ENGINEERING_PLAYBOOK.md](docs/ENGINEERING_PLAYBOOK.md).**
> Read it before your first non-trivial PR. It covers Kotlin idioms, architecture, data safety,
> testing, accessibility, performance, and what "done" actually means here. What follows is the
> short version.

### The rules you cannot skip

1. **Never risk user data.** FastBeat is offline — there is no server backup for someone's
   playlists or listening history. `fallbackToDestructiveMigration()` is banned, and every schema
   change ships a migration plus a migration test.
2. **No silent failure.** An empty `catch` is a defect. Log or propagate; never swallow.
3. **Evidence, not assertion.** "It works" needs a command and its output. Say what you did *not*
   verify rather than implying coverage you don't have.
4. **Match the surrounding code.** Two dialects cost more than either dialect saves.
5. **Accessibility is a requirement**, not polish — see [playbook §7](docs/ENGINEERING_PLAYBOOK.md#7-accessibility).

### Kotlin Style Guide

Follow the [Official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html);
`ktlint` enforces the mechanical parts, so let it do that work for you.

```kotlin
// ✅ DO: Use descriptive names and clear structure
fun playMedia(media: MediaFile) {
    if (media.isValid) {
        viewModel.play(media)
    }
}

// ❌ DON'T: Use obscure names or complex one-liners
fun p(m: MediaFile) = if (m.v) v.p(m) else null
```

Beyond formatting: `val` over `var`, sealed types over boolean pairs, no `!!`, no `GlobalScope`,
injected dispatchers rather than hardcoded ones, and named arguments once a call takes more than two
parameters.

### UI Component Guidelines

- Use `@Composable` functions for UI elements; state goes down, events come up.
- Keep UI components stateless wherever possible — a composable that takes a ViewModel belongs only
  at a screen root.
- Use `LocalAppTheme` for consistent styling.
- `collectAsStateWithLifecycle()`, never `collectAsState()` — the latter keeps working while the app
  is backgrounded, which costs real battery on a media app.
- Every async surface renders **four** states: loading, empty, error with a retry, and success.
- Icon-only controls need a `contentDescription` naming the action; decorative icons need
  `contentDescription = null`. Touch targets are at least 48dp.

---

## 📦 Building a release APK

`./gradlew assembleDebug` is what you want for day-to-day work. The **release** variant is a
different build: R8 minification and resource shrinking are on, and it is the only variant that
ships. CI now builds it on every push, so R8 breakage surfaces in a PR rather than on release day.

### Signing

The release build is signed only if credentials are available. Without them it still succeeds and
produces `FastBeat-release-unsigned.apk`, which **cannot be installed on a device or uploaded to
Play** — the build prints why. That fallback exists so forks and secret-less CI runs can still
prove the release variant compiles.

To produce an installable APK, create a keystore once:

```bash
keytool -genkeypair -v -keystore fastbeat-release.jks   -alias fastbeat -keyalg RSA -keysize 2048 -validity 10000
```

Then create `keystore.properties` in the repo root:

```properties
storeFile=/absolute/path/to/fastbeat-release.jks
storePassword=...
keyAlias=fastbeat
keyPassword=...
```

`.gitignore` covers `keystore.properties`, `*.jks` and `*.keystore`. **Never commit any of them.**
A leaked keystore lets anyone publish an update that Android accepts as genuine, and it cannot be
rotated for an app already installed on devices — the only recovery is a new application ID, which
every existing user has to install by hand.

In CI, set the same four values as secrets instead: `FASTBEAT_KEYSTORE_FILE`,
`FASTBEAT_KEYSTORE_PASSWORD`, `FASTBEAT_KEY_ALIAS`, `FASTBEAT_KEY_PASSWORD`. All four must be
present; a partial configuration is reported rather than silently ignored.

### Keep the mapping file

Every release you distribute must be archived with the `mapping.txt` produced by **that exact
build** (`app/build/outputs/mapping/release/`). It is the only thing that turns an obfuscated
production stack trace back into source lines, and rebuilding generates a different one. CI uploads
it as an artifact for 90 days.

### Versions are derived, never edited

`versionCode` is the commit count and `versionName` comes from `git describe`. Do not hand-edit
either — see the comment block at the top of `app/build.gradle.kts`, and `release.yml` for how the
tags they read get created.

---

## 🏷️ Commit Message Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

### Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat: add dual-audio track selector` |
| `fix` | Bug fix | `fix: resolve crash on Android 14+` |
| `docs` | Documentation | `docs: update GETTING_STARTED.md` |
| `style` | Formatting | `style: fix indentation in MainScreen` |
| `refactor` | Refactoring | `refactor: extract playback logic to Service` |
| `test` | Adding tests | `test: add unit tests for PlaybackViewModel` |
| `chore` | Maintenance | `chore: update Room version to 2.7.0` |

### Format
```
<type>(<scope>): <subject>

<body>

<footer>
```

---

## 🧪 Testing Guidelines

### Writing Tests
- Use **JUnit 4** for unit tests.
- Use **Compose UI Testing** library for UI tests.
- Test repositories with a mock database or `In-Memory` Room instance.

### Coverage Goals

| Component | Minimum |
|-----------|---------|
| ViewModels | 80% |
| Repositories | 85% |
| Models | 95% |
| UI Screens | 60% |

---

## 🐛 Reporting Bugs

### Bug Report Template
```markdown
### Describe the Bug
Clear and concise description of the bug.

### To Reproduce
1. Open 'Video Player'
2. Swipe up on 'Brightness' side
3. See 'Crash/Error'

### Expected Behavior
Brightness should increase smoothly.

### Environment
- Device: [e.g., Pixel 7]
- OS: [e.g., Android 14]
- FastBeat Version: [e.g., 1.0.0]
```

---

## 🔍 Pull Request Process

### PR Checklist
Before submitting:
- [ ] Code follows style guidelines.
- [ ] All tests are passing locally.
- [ ] Linter/Static analysis passes.
- [ ] Documentation updated if needed.
- [ ] Commit messages follow convention.

---

## 📞 Getting Help
- **Getting Started**: Read the [Getting Started Guide](docs/GETTING_STARTED.md)
- **Features**: Explore the [Features Guide](docs/FEATURES.md)
- **Issues**: Use [GitHub Issues](https://github.com/yourusername/FastBeat/issues) for bugs and features.

---

Thank you for contributing to FastBeat! 🎉
