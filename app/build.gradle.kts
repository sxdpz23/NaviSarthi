import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(navigationLibs.plugins.kotlin.ksp)
    alias(navigationLibs.plugins.kotlin.serialization)
    alias(navigationLibs.plugins.hilt)
    // Firebase is OPTIONAL / bring-your-own-project: the Google Services plugin is only
    // applied when a google-services.json is present. Without it the app builds and runs
    // fully local (no sync) — a rider who doesn't want multi-device sync just omits the
    // file. To enable sync, drop your own Firebase project's google-services.json in app/.
    alias(libs.plugins.google.services) apply false
}

if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    // Required by the Crashlytics SDK — it injects a build ID resource at build time, and
    // without it FirebaseInitProvider throws "Crashlytics build ID is missing" on EVERY launch.
    apply(plugin = "com.google.firebase.crashlytics")
}

// Release signing is loaded from a gitignored signing.properties at the repo root (see
// that file). It points at the stable release keystore — the SAME key every public APK
// is signed with, so updates install in place. If the file is absent (e.g. a contributor
// just building locally), the release build falls back to the debug keystore.
val signingProps = Properties()
rootProject.file("signing.properties").let { f ->
    if (f.exists()) f.inputStream().use { signingProps.load(it) }
}
val hasReleaseSigning = signingProps.getProperty("storeFile")?.let { File(it).exists() } == true

android {
    namespace = "com.sxdpz23.navisarthi"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.navisarthi.app"
        // NaviSarthi targets Android 14 and newer.
        minSdk = 34
        targetSdk = 36
        buildConfigField("String", "MAPTILER_KEY", "\"\"")
        buildConfigField("String", "OBF_MANIFEST_URL", "\"\"")
        buildConfigField("String", "OVERLAY_MANIFEST_URL", "\"\"")
        buildConfigField("String", "MAXSPEED_MANIFEST_URL", "\"\"")
        buildConfigField("String", "ADDRESS_MANIFEST_URL", "\"\"")
        buildConfigField("String", "PLACES_MANIFEST_URL", "\"\"")
        buildConfigField("String", "CELLS_MANIFEST_URL", "\"\"")
        buildConfigField("String", "BASEMAP_MANIFEST_URL", "\"\"")
        buildConfigField("String", "WORLD_BASEMAP_URL", "\"\"")
        buildConfigField("String", "POI_PACK_MANIFEST_URL", "\"\"")
        buildConfigField("String", "FLOCK_MANIFEST_URL", "\"\"")
        buildConfigField("String", "ROAD_FEATURES_MANIFEST_URL", "\"\"")
        buildConfigField("String", "MAP_FONTS_URL", "\"\"")
        // Independent NaviSarthi version sequence.
        versionCode = 38
        versionName = "0.6.7"

        // Test-channel telemetry: ride/connection diagnostics upload to Firestore so logs can be
        // pulled remotely (no adb) while iterating on test builds. MUST be flipped to "false" for
        // any real public release — see DiagnosticsUploader. Gated additionally on a configured
        // Firebase project, so it's a no-op on bring-your-own-keys builds without google-services.json.
        buildConfigField("boolean", "DIAG_UPLOAD", "true")

        // Ship arm64-v8a only: every modern OEM phone (OnePlus/Samsung/Xiaomi/Nothing…) is
        // 64-bit ARM; the other ABIs (armeabi-v7a/x86/x86_64) are old 32-bit or emulators. This
        // cuts the universal APK from ~107 MB to well under GitHub's 100 MB file limit so the
        // test build can live in the repo for direct download.
        ndk { abiFilters += "arm64-v8a" }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Hobby/open-source distribution: signed by the stable release keystore (from
            // signing.properties) so every published APK shares one signature and updates
            // install in place. Falls back to the debug keystore when signing.properties is
            // absent (contributors building locally) — that build just can't update users.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
            // Public release builds never upload diagnostics, and the test-build installer
            // (gated on BuildConfig.DEBUG) is absent — release stays clean automatically.
            buildConfigField("boolean", "DIAG_UPLOAD", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Imported locales are deliberately partial; Android falls back to the default English strings.
        // Keep coverage findings visible, without treating missing translations as runtime failures.
        warning += "MissingTranslation"
    }
    buildFeatures {
        compose = true
        // Generates BuildConfig.DEBUG — used to gate sensitive logging (GPS coordinates,
        // shared-location text/URLs, the signed-in uid, raw dash packets) out of release
        // builds. See util/Dbg.kt.
        buildConfig = true
    }
}

dependencies {

    implementation(project(mapOf("path" to ":navigation-runtime", "configuration" to "shadow")))
    implementation(files("libs/navigation/osmand-shared-jvm.jar", "libs/navigation/gnu-trove-osmand.jar", "libs/navigation/kxml2-android.jar"))
    implementation("commons-logging:commons-logging:1.4.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation(navigationLibs.androidx.core.ktx)
    implementation(navigationLibs.androidx.webkit)
    implementation(navigationLibs.androidx.lifecycle.runtime.ktx)
    implementation(navigationLibs.androidx.lifecycle.runtime.compose)
    implementation(navigationLibs.androidx.lifecycle.viewmodel.compose)
    implementation(navigationLibs.androidx.activity.compose)
    implementation(navigationLibs.androidx.splashscreen)
    implementation(navigationLibs.androidx.compose.ui)
    implementation(navigationLibs.androidx.compose.ui.graphics)
    implementation(navigationLibs.androidx.compose.ui.tooling.preview)
    implementation(navigationLibs.androidx.compose.foundation)
    implementation(navigationLibs.androidx.compose.material.icons.extended)
    implementation(navigationLibs.androidx.material3)
    implementation(navigationLibs.androidx.navigation.compose)
    implementation(navigationLibs.hilt.android)
    implementation(navigationLibs.hilt.navigation.compose)
    implementation(navigationLibs.coil.compose)
    implementation(navigationLibs.maplibre.android)
    implementation(navigationLibs.androidx.car.app)
    implementation(navigationLibs.androidx.car.app.projected)
    implementation(navigationLibs.kotlinx.coroutines.android)
    implementation(navigationLibs.kotlinx.serialization.json)
    implementation(navigationLibs.androidx.datastore.preferences)
    implementation(navigationLibs.okhttp)
    implementation(navigationLibs.rhino.runtime)

    ksp(navigationLibs.hilt.compiler)
    implementation(files("libs/navigation/sherpa-onnx-1.13.3.aar", "libs/navigation/cronet-155.0.8059.16.aar"))

    implementation("com.google.android.gms:play-services-auth:21.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Firebase product suite (all gated at runtime behind FirebaseGate — no google-services.json
    // ⇒ default FirebaseApp never initializes ⇒ these are dormant and the app runs fully local).
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    // NDK crash capture — JVM CrashGuard can't see native SIGSEGVs (MapLibre/MediaCodec); this
    // routes them to the Crashlytics console so a native crash is no longer completely untraced.
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    // Debug-only App Check provider (loaded via reflection in FirebaseFeatures) so the
    // release classpath stays on Play Integrity only.
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.maplibre)
    implementation(libs.maplibre.annotation) { exclude(group = "org.maplibre.gl", module = "android-sdk") }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
