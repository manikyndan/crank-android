import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

// Export the Room schema so migrations can be verified against a known-good
// snapshot. Without this, a migration bug only surfaces on a user's device.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "com.crank.music"
    compileSdk {
        version = release(37)
    }

    /**
     * Release signing, loaded from [keystorePropertiesFile] when it exists.
     *
     * Deliberately NOT defaulted to the debug keystore. A release build signed with
     * the debug key cannot be published or updated on any device that has the real
     * app installed (signature mismatch), so failing is better than silently
     * producing an APK that looks fine and is actually unusable.
     *
     * To produce a signed release build, create `keystore.properties` in the repo
     * root (git-ignored) with:
     *     storeFile=../crank-release.jks
     *     storePassword=...
     *     keyAlias=...
     *     keyPassword=...
     * Without it, `assembleRelease` still works — it just emits
     * `app-release-unsigned.apk`, which is fine for local verification and for
     * `apksigner`-in-CI setups.
     */
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }
    val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // Resolve against rootProject, not the module: `file()` inside
                // `android {}` is relative to app/, so a `storeFile` of
                // `../crank-release.jks` would silently land in the wrong place.
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.crank.music"
        minSdk = 24
        targetSdk = 37
        // Must stay ahead of whatever is installed on test devices. The debug
        // builds previously installed on the SM-A356E were versionCode 3, while
        // this file had drifted to 1 — so installing over them failed with
        // INSTALL_FAILED_VERSION_DOWNGRADE, and `connectedDebugAndroidTest`
        // reported success while silently running zero tests as a result.
        // Always bump this before installing over an existing build.
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                // Keep rules for classes that are referenced but absent on Android
                // (optional-dependency probes in Coil, Rhino and Ktor). Without
                // this file R8 fails the release build outright.
                "missing_rules.txt"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            // Production code logs through android.util.Log, which the JVM test runtime stubs out
            // and which throws by default. Returning defaults instead lets unit tests reach the
            // code under test rather than dying on a log line.
            isReturnDefaultValues = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // InnerTube no longer needs an API key for public content, so this defaults to
    // empty. Supply INNERTUBE_API_KEY in local.properties only if a key is ever
    // genuinely required again — that keeps it out of version control.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { stream -> load(stream) }
    }

    val innerTubeApiKey: String = localProps.getProperty("INNERTUBE_API_KEY") ?: ""

    // AudD powers "Recognize Song". Unlike InnerTube, this one genuinely IS a
    // credential: it is billed per request, so it must never be committed.
    // Defaults to empty, and the recognition UI reports a clear "not configured"
    // state rather than silently failing.
    val auddApiToken: String = localProps.getProperty("AUDD_API_TOKEN") ?: ""

    defaultConfig {
        buildConfigField("String", "INNERTUBE_API_KEY", "\"$innerTubeApiKey\"")
        buildConfigField("String", "AUDD_API_TOKEN", "\"$auddApiToken\"")
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Hilt - Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room - Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coil - Image Loading for Compose
    implementation(libs.coil.compose)
    implementation(libs.coilNetworkOkhttp)

    // Navigation - Compose Navigation
    implementation(libs.androidx.navigation.compose)

    // Palette - dominant color extraction for the Now Playing gradient
    implementation(libs.androidx.palette)

    // Media3 ExoPlayer - Audio Playback & Downloads
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)

    // Kotlinx Serialization JSON
    implementation(libs.kotlinx.serialization.json)

    // Ktor Client
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    // NewPipe Extractor
    implementation(libs.newpipeExtractor)

    testImplementation(libs.junit)
    // MockEngine — lets the lyrics source be exercised over a canned HTTP response, so the
    // "response thrown away during parsing" class of bug is caught without a network.
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // MigrationTestHelper — verifies each Migration against the exported schema
    // snapshots in app/schemas, so a broken upgrade fails in CI rather than on a
    // user's device.
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
