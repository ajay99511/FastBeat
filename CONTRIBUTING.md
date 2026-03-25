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

### Kotlin Style Guide
Follow the [Official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ DO: Use descriptive names and clear structure
fun playMedia(media: MediaFile) {
    if (media.isValid) {
        viewModel.play(media)
    }
}

// ❌ DON'T: Use obscure names or complex one-liners
fun p(m: MediaFile) = if(m.v) v.p(m) else null
```

### UI Component Guidelines
- Use `@Composable` functions for UI elements.
- Keep UI components stateless whenever possible.
- Use `LocalAppTheme` for consistent styling.

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
