plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
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
                "proguard-rules.pro",
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

    // Room exports its schema JSON to $projectDir/schemas (see the ksp {} block below).
    // MigrationTestHelper reads those files from the instrumentation APK's *assets* at runtime, so
    // the directory must be packaged into the androidTest APK explicitly -- Room does not do this
    // for you. Without it a migration test fails at runtime with "Cannot find the schema file",
    // which reads like a migration bug when it is really a build-config bug. Hence P3-A exists as
    // a separate task from P3-D.
    // NOTE: `sourceSets` belongs to the android {} (ApplicationExtension) block. The `base {}` and
    // `ksp {}` blocks in this file are Project-level extensions and sit at the top level -- do not
    // copy their placement here.
    sourceSets.getByName("androidTest") {
        // srcDirs() appends; it does not replace src/androidTest/assets.
        assets.srcDirs("$projectDir/schemas")
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

// Static analysis. `buildUponDefaultConfig` keeps detekt's built-in rules and layers detekt.yml
// on top, so the config file only records our deviations rather than restating everything.
//
// A BASELINE is used deliberately: the point of adding detekt now, before the P4-E decomposition,
// is that pre-existing violations get recorded once and every *newly written* file is held to the
// full rule set. Baselining after the extraction would silently absolve the new code too.
// Regenerate with `./gradlew detektBaseline` only when you have consciously accepted new debt.
//
// Type resolution is NOT enabled: detekt 1.23.8 embeds a Kotlin 1.9 compiler and this project is on
// Kotlin 2.2.10, so `detektMain` (the type-resolving variant) is not reliable here. The syntax-only
// analysis still catches the majority of rules.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/detekt.yml"))
    baseline = file("$rootDir/detekt-baseline.xml")
    source.setFrom(files("src/main/java", "src/test/java", "src/androidTest/java"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
        txt.required.set(false)
    }
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "11"
}

// Set Kotlin JVM target (replaces the deprecated android { kotlinOptions {} } DSL)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// ---------------------------------------------------------------------------------------------
// Instrumentation classpath hygiene.
//
// mockk-android drags in JUnit 5 (Jupiter 5.8.2) transitively. Instrumentation tests execute under
// AndroidJUnitRunner, which is a JUnit *4* runner -- Jupiter cannot run there at all. Worse, its six
// jars each ship META-INF/LICENSE.md, and the resource merger refuses to collapse them, so
// :app:mergeDebugAndroidTestJavaResource fails and the whole androidTest source set is unbuildable.
//
// This excludes the unusable artifacts at the source rather than masking the symptom with a global
// packaging { resources { excludes } } block -- that would also alter the *shipped* APK's contents,
// which is a change to production output for a test-only problem.
//
// Scoped to androidTest only. The JVM unit-test classpath keeps JUnit 5, which Kotest requires via
// useJUnitPlatform().
// ---------------------------------------------------------------------------------------------
configurations.named("androidTestImplementation") {
    exclude(group = "org.junit.jupiter")
    exclude(group = "org.junit.platform")
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

    // Parses the legacy playlists.json file — see PlaylistRepository.migrateLegacyData
    implementation(libs.kotlinx.serialization.json)

    // Baseline Profile (P5-E). `profileinstaller` is what actually applies the profile on devices
    // that do not receive it through Play; without it the profile ships but never takes effect.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // Preferences DataStore, not Proto: the sort keys are generated at runtime
    // (audio_sort_$playlistId and friends), an unbounded key space Proto cannot model
    // without restructuring into an explicit map. See OQ-3.
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.room.compiler)

    // Adaptive Layouts
    implementation(libs.window)
    implementation(libs.adaptive)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)

    // ---------- Unit tests (src/test, JVM + Robolectric, JUnit Platform) ----------
    testImplementation(libs.kotest.runner.junit5) // Kotest on the JUnit 5 platform
    testImplementation(libs.kotest.assertions.core) // shouldBe / shouldContain matchers
    testImplementation(libs.kotest.property) // property-based testing
    testImplementation(libs.kotest.extensions.robolectric) // @RobolectricTest for Kotest specs
    testImplementation(libs.mockk) // idiomatic Kotlin mocking
    testImplementation(libs.turbine) // Flow/StateFlow assertions
    testImplementation(libs.kotlinx.coroutines.test) // runTest, TestDispatcher
    testImplementation(libs.robolectric) // Android framework on the JVM
    testImplementation(libs.androidx.arch.core.testing) // InstantTaskExecutorRule
    testImplementation(libs.androidx.test.core.ktx) // ApplicationProvider, etc.
    testImplementation(libs.room.testing) // in-memory Room + migration tests
    // Robolectric is a JUnit *4* runner, and `kotest-extensions-robolectric` 0.5.0 is a
    // Kotest-4-era artifact (it targets kotest 4.6.3 / robolectric 4.6.1, its extension class
    // is `internal`, and 0.5.0 is the newest release that exists) -- so it cannot drive
    // Robolectric under Kotest 5.9.1. Specs needing an Android Context therefore run as JUnit 4
    // via the vintage engine, alongside the Kotest specs on the same JUnit Platform.
    testImplementation(libs.junit) // JUnit 4 API for Robolectric-driven specs
    testRuntimeOnly(libs.junit.vintage.engine) // runs those JUnit 4 specs on JUnit Platform

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
    androidTestImplementation(libs.hilt.android.testing) // HiltAndroidRule, @HiltAndroidTest
    // MigrationTestHelper is a JUnit4 TestRule that needs an Instrumentation context, so it has to
    // be here. The existing testImplementation(libs.room.testing) stays -- P4-A uses it for
    // in-memory DAO tests on the JVM. Both source sets legitimately need the artifact.
    androidTestImplementation(libs.room.testing) // MigrationTestHelper
    kspAndroidTest(libs.hilt.compiler)

    // ---------- Debug-only tooling for Compose tests ----------
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
