plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

/**
 * Keeps kotlin-stdlib at the version the Kotlin toolchain can actually read.
 *
 * A library built against a newer Kotlin drags in a stdlib whose class metadata the compiler
 * refuses, and the error it produces names kotlin.Unit rather than the dependency that caused it.
 * AGP 9 bundles its own Kotlin, so the toolchain version is not ours to raise.
 */
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
        }
    }
}
