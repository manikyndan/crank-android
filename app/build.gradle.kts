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

    /**
     * Lint is a build gate, not advice.
     *
     * See the promoted/disabled check lists inside for what is enforced and what is accepted.
     */
    lint {
        abortOnError = true
        // A warning is still printed but does not fail the build; only the promoted checks below do.
        warningsAsErrors = false
        // Never let the gate pass because a check crashed or was superseded.
        checkDependencies = true
        // Surface the full picture in CI logs rather than only the first offender.
        textReport = true
        htmlReport = true

        // Promoted from warning to error. Each caught a real regression in this codebase and each is
        // now at zero findings, so the gate only fires on a new violation:
        //   UnusedResources        — dead strings outlived the UI that referenced them.
        //   UseKtx                 — `Uri.parse` / `edit().apply()` where androidx-core extensions exist.
        //   UnsafeOptInUsageError  — using a Media3 `@UnstableApi` symbol without opting in was a real
        //                            build failure here, and it is how a dependency bump turns into a
        //                            silent behavioural change.
        error.add("UnusedResources")
        error.add("UseKtx")
        error.add("UnsafeOptInUsageError")

        // Reported but not failing. These stay in the lint output on purpose: silencing a check would
        // also hide its *next* finding, and the point of a gate is to keep the signal visible while
        // only stopping the build for things that are unambiguously wrong.
        //
        //   GradleDependency / NewerVersionAvailable / AndroidGradlePluginVersion
        //                          — upgrade suggestions. Following them is a deliberate, tested
        //                            decision; they should be readable, not blocking.
        //   ExportedService        — the Media3 session service is exported on purpose: the Android
        //                            platform, System UI, Bluetooth media buttons and Android Auto all
        //                            bind to it from outside the app, so an `android:permission` guard
        //                            would break lock-screen and headset controls rather than secure
        //                            anything. Access control lives where it can actually see the
        //                            caller — the package / uid allowlist in
        //                            `CrankSessionCallback.onConnect`, which rejects every controller
        //                            that is not this app or a trusted system component. Lint cannot see
        //                            across that call. Kept enabled so a *second* exported service
        //                            would still show up.
        //   UnusedAttribute        — a manifest attribute with no effect at the current targetSdk.

        // Suppressed outright. These are pure noise for this module:
        //   IconLauncherShape / IconDuplicates
        //                          — the launcher and round icon files are byte-identical, which is a
        //                            deliberate artwork decision rather than a defect.
        //   SetJavaScriptEnabled   — required by the BotGuard WebView, which must execute its token
        //                            script. There is no narrower way to run it.
        disable.add("IconLauncherShape")
        disable.add("IconDuplicates")
        disable.add("SetJavaScriptEnabled")
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

    // Where the app looks for a published release manifest. There is no release host yet, and this
    // deliberately does not default to a placeholder URL: an unreachable guess would make the update
    // screen report "couldn't check" for the wrong reason. Empty means the feature honestly reports
    // that it cannot check, and supplying a URL in local.properties enables it with no code change.
    val updateManifestUrl: String = localProps.getProperty("CRANK_UPDATE_MANIFEST_URL") ?: ""

    defaultConfig {
        buildConfigField("String", "INNERTUBE_API_KEY", "\"$innerTubeApiKey\"")
        buildConfigField("String", "AUDD_API_TOKEN", "\"$auddApiToken\"")
        buildConfigField("String", "CRANK_UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }
}

/**
 * Names the built APK after the product rather than after the Gradle module.
 *
 * The filename is what a recipient actually sees when the app is shared, and `app-debug.apk`
 * says nothing about what they are installing.
 *
 * The release build gets the clean name because it is the one that gets shared; debug keeps a
 * `debug` marker so the two can never be confused for each other in a downloads folder. The
 * space is deliberate — this is a display name, not a build identifier.
 */
androidComponents {
    onVariants { variant ->
        val name = if (variant.buildType == "release") {
            "Crank Music.apk"
        } else {
            "Crank Music ${variant.name}.apk"
        }
        variant.outputs.forEach { output ->
            output.outputFileName.set(name)
        }
    }
}

/**
 * Reports whether the BotGuard asset is present when a build is assembled.
 *
 * `po_token.html` cannot be committed (upstream lists it as sensitive), so it is absent from
 * every fresh checkout. A build without it still installs and plays *some* tracks, but YouTube
 * rejects most streams with HTTP 403 — a failure that only shows up at play time, on a user's
 * device, long after the build looked successful.
 *
 * Deliberately non-fatal by default: failing here would break every build for anyone without the
 * asset, including CI. The warning surfaces the problem where it is cheapest to notice, and
 * `-PrequireBotGuard=true` escalates it to a hard failure for a pipeline expected to ship it.
 */
val verifyBotGuardAsset by tasks.registering {
    val asset = layout.projectDirectory.file("src/main/assets/po_token.html")
    val requireAsset = providers.gradleProperty("requireBotGuard").orNull == "true"
    outputs.upToDateWhen { false }
    doLast {
        if (!asset.asFile.exists()) {
            val message = "BotGuard asset 'app/src/main/assets/po_token.html' is MISSING. " +
                "Playback cannot mint Proof-of-Origin tokens without it, so most tracks will be " +
                "rejected with HTTP 403. Supply the asset locally before shipping this APK."
            if (requireAsset) error(message) else logger.warn("WARNING: $message")
        }
    }
}

tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("bundle") }
    .configureEach { finalizedBy(verifyBotGuardAsset) }

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
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
    // HLS: the Gaana source returns .m3u8 master playlists, and Media3 only recognises
    // them when this module is on the classpath (see PlayerModule's DefaultMediaSourceFactory).
    implementation(libs.media3.exoplayer.hls)
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

    // Scaffold (com.manikyndan.crank) sample graph: Retrofit + OkHttp + DataStore.
    // Kept additive — production code uses Ktor/NewPipe/InnerTube, not Retrofit.
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    // MockEngine — lets the lyrics source be exercised over a canned HTTP response, so the
    // "response thrown away during parsing" class of bug is caught without a network.
    testImplementation(libs.ktor.client.mock)
    // Scaffold (com.manikyndan.crank) tests: JUnit5 + MockK + coroutines-test.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk.unit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Scaffold Compose UI tests.
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    // MigrationTestHelper — verifies each Migration against the exported schema
    // snapshots in app/schemas, so a broken upgrade fails in CI rather than on a
    // user's device.
    androidTestImplementation(libs.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
