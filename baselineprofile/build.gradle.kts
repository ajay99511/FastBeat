// Baseline Profile generator (P5-E).
//
// TO REGENERATE THE PROFILE:
//   ./gradlew :app:generateBaselineProfile
// with exactly one device attached, or ANDROID_SERIAL set to pick one. Output lands in
// app/src/release/generated/baselineProfiles/ and is picked up by the release variant.
//
// This is a RELEASE-TIME step, not a per-commit one: the profile is a generated artifact that
// reflects whatever media library the generating device had, and it goes stale as the code moves.
// Nothing in the normal build or CI depends on it — the app builds and ships without it, just
// without the ahead-of-time compilation win.
//
// Emulator notes, learned the hard way:
//  - Needs real free space. A near-full AVD fails with "Requested internal only, but not enough
//    space" while installing the ~50 MB test APK, which reads like a permissions problem.
//  - Launch the emulator with `-no-audio`. The journeys start playback, and the emulator's audio
//    backend took the whole emulator process down mid-run (the test reports "device offline").
//    Disabling host audio costs nothing here: ExoPlayer still runs the full playback path.
//  - Root is NOT required. Generation works on a Play Store image at API 33+.
plugins {
    // No `kotlin.android` here: AGP 9 applies Kotlin itself and registers the `kotlin` extension,
    // so declaring it again fails with "extension already registered". `:app` omits it for the
    // same reason.
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.local.offlinemediaplayer.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // Macrobenchmark drives a real, non-debuggable build of the app, so it cannot use
        // Robolectric or a JVM runner: minSdk 28 is the floor for profile generation.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // This module *only* generates profiles; it has no product code and no debug variant worth
    // building. Restricting it keeps `./gradlew build` from producing variants nobody runs.
    targetProjectPath = ":app"
}

baselineProfile {
    // One device, one run. Profile quality improves with more iterations, but each iteration is a
    // full install + launch cycle on an emulator; raise this when generating a profile for a
    // release rather than on every developer machine.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
