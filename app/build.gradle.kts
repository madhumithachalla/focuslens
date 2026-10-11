import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Gradle does NOT automatically expose custom local.properties keys via project.findProperty()
// — that only works for real Gradle/system properties. AGP itself only reads local.properties
// for sdk.dir/ndk.dir internally. Without this explicit load, SPOTIFY_CLIENT_ID and
// GOOGLE_WEB_CLIENT_ID were silently always empty in every build, CI included, regardless of
// what the CI step wrote into the file or what secret was configured — which is why Google
// sign-in always hit the "needs one more setup step" branch no matter what.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.focuslens"
    // Bumped from 34: androidx.health.connect:connect-client 1.1.0 requires compiling
    // against at least API 36, which in turn needs AGP 8.9.1+ (see build.gradle.kts).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.focuslens"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // From the Spotify Developer Dashboard (developer.spotify.com/dashboard) — create an
        // app there, add redirect URI focuslens://auth, and paste the Client ID into
        // local.properties as SPOTIFY_CLIENT_ID=... (gitignored, so it's never committed).
        val spotifyClientId = localProperties.getProperty("SPOTIFY_CLIENT_ID") ?: ""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")

        // Firebase console → Authentication → Sign-in method → Google → "Web SDK
        // configuration" shows a Web client ID once Google sign-in is enabled. Paste it into
        // local.properties as GOOGLE_WEB_CLIENT_ID=... (gitignored). Needed as the
        // serverClientId for Credential Manager's Google ID flow in AccountScreen.kt.
        val googleWebClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // A fixed, checked-in debug keystore (app/debug.keystore) instead of Gradle's default
        // per-machine one (~/.android/debug.keystore). Google Sign-In validates the app's
        // signing-certificate SHA-1 against what's registered in the Firebase console — with
        // the default keystore, CI would regenerate a different one (and a different SHA-1)
        // on every run, permanently breaking that validation. This keystore's passwords are
        // the well-known Android debug defaults; it signs debug builds only, never release.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // No composeOptions{ kotlinCompilerExtensionVersion } block: that mechanism only applied
    // to the old K1 compiler (Kotlin 1.9.x). With Kotlin 2.x, the
    // org.jetbrains.kotlin.plugin.compose plugin applied above handles the Compose compiler,
    // version-aligned with the Kotlin version automatically.
}

// The old android{ kotlinOptions { jvmTarget = "17" } } (String-typed) is a hard compile
// error on this Kotlin Gradle plugin version — migrated to the compilerOptions DSL.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Bumped from 2.6.1: that version predates Room's KSP2 compatibility fixes and hits a
    // real KSP2 bug ("unexpected jvm signature V") on Unit-returning suspend DAO methods —
    // confirmed still present even on KSP 2.3.12, so this needs a newer Room, not a KSP
    // workaround (KSP1, the previous workaround, was removed entirely in KSP 2.3.x).
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Spotify's official App Remote SDK. Confirmed directly from spotify/android-sdk's own
    // README and demo app build.gradle: this is distributed ONLY as a prebuilt .aar checked
    // into that repo (app-remote-lib/spotify-app-remote-release-0.8.0.aar), never published
    // to Google's Maven, Maven Central, or JitPack — an earlier guessed Maven coordinate here
    // failed in CI because that artifact genuinely doesn't exist anywhere. gson is the one
    // real transitive dependency, per that same demo app.
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.8.6")

    // Optional, user-initiated import of period-start dates from Health Connect (the
    // system-level store a watch or health app can write cycle data into). Read-only, never
    // writes anything back, and only runs when the user taps "Import from watch."
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Same Firebase project as the web app (focuslens-8805c) — shares accounts and data
    // once community/accountability features are built, via app/google-services.json.
    // Using the plain (non "-ktx") artifacts: Firebase merged the KTX extensions into the
    // base libraries a while back, and the BoM no longer manages the old "-ktx" coordinates.
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // "Continue with Google" on the Account screen: Credential Manager is the current
    // (non-deprecated) Android API for this, replacing the old Google Sign-In SDK.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
