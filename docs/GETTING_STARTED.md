# 🚀 Getting Started with FastBeat

This guide will help you set up your development environment and start contributing to FastBeat.

---

## 📋 Prerequisites

### Required Software

| Software | Version | Purpose | Download |
|----------|---------|---------|----------|
| **Android Studio** | Iguana+ | Primary IDE for development | [Download](https://developer.android.com/studio) |
| **JDK** | 11 or 17 | Java Development Kit for builds | [Download](https://adoptium.net/) |
| **Android SDK** | API 35 | Target SDK for the application | Install via SDK Manager |
| **Git** | Latest | Version control | [Download](https://git-scm.com/) |

### Platform-Specific Requirements

#### Windows
Ensure you have the **Android Emulator** or a physical device with **USB Debugging** enabled.
```powershell
# Check Java version
java -version

# Check Gradle (using wrapper)
.\gradlew --version
```

#### macOS / Linux
```bash
# Check Java version
java -version

# Check Gradle (using wrapper)
./gradlew --version
```

---

## 📥 Installation

### Step 1: Clone the Repository

```bash
git clone https://github.com/yourusername/FastBeat.git
cd FastBeat
```

### Step 2: Verify Installation

```bash
# Run a clean build to ensure all dependencies are fetched
./gradlew clean build
```

### Step 3: Install Dependencies
FastBeat uses the **Gradle Version Catalog** (`gradle/libs.versions.toml`). Dependencies are automatically managed when you sync the project in Android Studio.

### Step 4: Run the Application

#### Using Android Studio
1. Open the project in Android Studio.
2. Select your device/emulator in the toolbar.
3. Click the **Run** button (Play icon) or press `Shift + F10`.

#### Using CLI
```bash
# Install on connected device
./gradlew installDebug
```

---

## 🛠️ Development Workflow

### Hot Reload / Compose Preview
Jetpack Compose allows you to preview UI components instantly.
- Open any `@Preview` annotated function.
- Use the **Split** or **Design** view in Android Studio.
- Use **Live Edit** for real-time UI updates on a physical device.

### Debugging
- Use the **Logcat** window to view system logs and app-specific tags (`FastBeat_Trace`).
- Set breakpoints in the IDE and run the app in **Debug Mode** (`Shift + F9`).
- Use **Layout Inspector** to debug the Compose UI hierarchy.

---

## 🧪 Testing

### Run All Tests

```bash
# Unit Tests
./gradlew test

# Instrumentation (UI) Tests
./gradlew connectedAndroidTest
```

### Running Specific Tests
In Android Studio, right-click on a test class or method and select **Run 'TestName'**.

---

## 📁 Project Structure

```text
OfflineMediaPlayer/
├── app/
│   ├── build.gradle.kts      # App-level build configuration
│   └── src/main/java/com/local/offlinemediaplayer/
│       ├── data/             # Room Database, DAOs, and DI Modules
│       ├── model/            # Data models (MediaFile, Album, Playlist)
│       ├── repository/       # Data Layer (MediaRepository, PlaylistRepository)
│       ├── service/          # Media3 PlaybackService (MediaSession)
│       ├── ui/               # UI Layer (Screens, Theme, Components)
│       │   ├── screens/      # Feature screens (Video, Audio, Me)
│       │   ├── theme/        # Material 3 Theme & Colors
│       │   └── navigation/   # NavHost configurations
│       └── viewmodel/        # MVVM ViewModels (Playback, Analytics)
├── gradle/
│   └── libs.versions.toml    # Centralized dependency management
└── docs/                     # Project documentation
```

---

## 🔧 Common Development Tasks

### Adding a New Screen
1. Create a new `@Composable` in `ui/screens/`.
2. Add the screen to the relevant `NavigationHost` in `ui/navigation/`.
3. If data-backed, create a new `ViewModel` in `viewmodel/`.

### Modifying the Database
1. Update entities in `data/db/Entities.kt`.
2. Update the `AppDatabase` version.
3. Use `ksp` to regenerate Room boilerplate:
   ```bash
   ./gradlew kspDebugKotlin
   ```

---

## 🐛 Troubleshooting

### Common Issues

#### "Hilt: Component not found"
**Solution:**
Ensure your Activity or Fragment is annotated with `@AndroidEntryPoint`.
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }
```

#### "Room: Schema export failed"
**Cause:** Schemas folder missing or path incorrect in `build.gradle.kts`.
**Solution:**
```bash
mkdir app/schemas
./gradlew assembleDebug
```

#### "Media3: Player is released"
**Solution:**
Check the `PlaybackService` lifecycle and ensure the `ExoPlayer` instance is properly managed in the `MediaSession`.

---

## 📚 Resources

### Official Documentation
- [Jetpack Compose Guide](https://developer.android.com/jetpack/compose)
- [AndroidX Media3 Documentation](https://developer.android.com/guide/topics/media/media3)
- [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- [Dagger Hilt on Android](https://developer.android.com/training/dependency-injection/hilt-android)

### Project Documentation
- [Features Guide](FEATURES.md)
- [Contributing Guidelines](../CONTRIBUTING.md)
- [Architecture Overview](../README.md#-architecture)

---

## ✅ Next Steps

1. **Explore the codebase** - Start with `MainActivity.kt` and `MainScreen.kt`.
2. **Run the app** - Try playing a local video or audio file.
3. **Customize themes** - Explore `ui/theme/` and try adding a new color palette.
4. **Read the roadmap** - Check `docs/FEATURES.md` for upcoming tasks.

Happy coding! 🎉
