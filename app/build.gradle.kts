plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)  // ✅ Changed from explicit version to alias
    id("com.google.dagger.hilt.android") version "2.58"
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

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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

    applicationVariants.all {
        outputs.all {
            // In Kotlin DSL, we must cast to 'BaseVariantOutputImpl' to set the file name
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "FastBeat.apk"
        }
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

    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Hilt Dependency Injection
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("com.google.dagger:hilt-android:2.58")
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.ui)
    ksp("com.google.dagger:hilt-compiler:2.58")

    // Navigation for switching screens
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 (Modern ExoPlayer replacement)
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-session:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")

    // Coil for loading album art
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Lifecycle integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Gson for JSON Persistence
    implementation("com.google.code.gson:gson:2.10.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Adaptive Layouts
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")

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
