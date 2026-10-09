plugins {
    // Bumped from 8.9.1: KSP 2.3.12 (decoupled versioning, needed for Kotlin 2.4.21)
    // requires AGP 8.12.0 or newer.
    id("com.android.application") version "8.12.0" apply false
    // Bumped from 1.9.24, then 2.1.21: firebase-firestore/firebase-common were fine at
    // Kotlin 2.1 metadata, but firebase-auth 25.0.0 specifically ships 2.4.0-format
    // metadata, which needs a compiler at least that new to read.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    // Compose's compiler is now this separate Gradle plugin (the K2 architecture) instead
    // of the old composeOptions{ kotlinCompilerExtensionVersion } mechanism, which only
    // worked with the Kotlin 1.9.x (K1) compiler.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // KSP switched to Kotlin-independent versioning (no more "<kotlin>-<ksp>" coordinate) —
    // this release supports current Kotlin 2.x versions including 2.4.21.
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
