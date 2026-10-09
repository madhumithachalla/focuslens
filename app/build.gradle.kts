plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
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
        val spotifyClientId = project.findProperty("SPOTIFY_CLIENT_ID") as String? ?: ""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

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
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
}
