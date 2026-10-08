plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.focuslens"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.focuslens"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // From the Spotify Developer Dashboard (developer.spotify.com/dashboard) — create an
        // app there, add this redirect URI, and paste the Client ID into local.properties as
        // SPOTIFY_CLIENT_ID=... (gitignored, so it's never committed).
        val spotifyClientId = project.findProperty("SPOTIFY_CLIENT_ID") as String? ?: ""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        manifestPlaceholders["spotifyRedirectScheme"] = "focuslens"
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

    // Spotify's official App Remote SDK — controls playback in the user's installed Spotify
    // app directly (play/pause/queue a playlist URI); no separate web login needed.
    // NOTE: I can't reach the internet from this sandbox to confirm the current exact
    // artifact coordinates/version. Spotify distributes this as an .aar via GitHub releases
    // (github.com/spotify/android-sdk/releases) rather than a normal Maven artifact in most
    // versions — when you open this in Android Studio, check that page for the current
    // recommended include method (JitPack coordinate or a local .aar in app/libs/) and swap
    // the line below if it doesn't resolve as-is.
    implementation("com.spotify.android:auth:2.1.1")
    implementation("com.github.spotify:android-sdk:release-2.2.0")
}
