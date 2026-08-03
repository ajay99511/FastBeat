plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.local.offlinemediaplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.offlinemediaplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest + resources on the test classpath.
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

// AGP 9.0 archivesName — produces FastBeat-debug.apk / FastBeat-release.apk.
// `base` is a Project-level extension (from the base plugin), so it must live at
// the top level, not inside the android {} (ApplicationExtension) block.
base {
    archivesName = "FastBeat"
}

// KSP config (Room schema export). `ksp` is a Project-level extension from the
// KSP plugin, so — like `base` — it belongs at the top level, not inside android {}.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Set Kotlin JVM target (replaces the deprecated android { kotlinOptions {} } DSL)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.google.material)
    implementation(libs.material.icons.extended)

    // Hilt Dependency Injection
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.ui)
    ksp(libs.hilt.compiler)

    // Navigation for switching screens
    implementation(libs.navigation.compose)

    // Media3 (Modern ExoPlayer replacement)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // Coil for loading album art
    implementation(libs.coil.compose)

    // Lifecycle integration
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Gson for JSON Persistence
    implementation(libs.gson)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Adaptive Layouts
    implementation(libs.window)
    implementation(libs.adaptive)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)

    // ---------- Unit tests (src/test, JVM + Robolectric, JUnit Platform) ----------
    testImplementation(libs.kotest.runner.junit5)     // Kotest on the JUnit 5 platform
    testImplementation(libs.kotest.assertions.core)    // shouldBe / shouldContain matchers
    testImplementation(libs.kotest.property)           // property-based testing
    testImplementation(libs.kotest.extensions.robolectric) // @RobolectricTest for Kotest specs
    testImplementation(libs.mockk)                     // idiomatic Kotlin mocking
    testImplementation(libs.turbine)                   // Flow/StateFlow assertions
    testImplementation(libs.kotlinx.coroutines.test)   // runTest, TestDispatcher
    testImplementation(libs.robolectric)               // Android framework on the JVM
    testImplementation(libs.androidx.arch.core.testing) // InstantTaskExecutorRule
    testImplementation(libs.androidx.test.core.ktx)    // ApplicationProvider, etc.
    testImplementation(libs.room.testing)              // in-memory Room + migration tests

    // ---------- Instrumented tests (src/androidTest, on-device/emulator) ----------
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)   // HiltAndroidRule, @HiltAndroidTest
    kspAndroidTest(libs.hilt.compiler)

    // ---------- Debug-only tooling for Compose tests ----------
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
