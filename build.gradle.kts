plugins {
    id("com.android.application") version "8.9.1" apply false
    // Bumped from 1.9.24: current Firebase libraries (firebase-bom 35.0.0) ship Kotlin
    // metadata in the 2.1 binary format, which a 1.9.x compiler can't read at all — this
    // isn't a version-pinning choice, Firebase just requires a Kotlin 2.x consumer now.
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Compose's compiler is now this separate Gradle plugin (the K2 architecture) instead
    // of the old composeOptions{ kotlinCompilerExtensionVersion } mechanism, which only
    // worked with the Kotlin 1.9.x (K1) compiler.
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-1.0.29" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}
