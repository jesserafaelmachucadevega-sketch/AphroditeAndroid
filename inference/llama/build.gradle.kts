plugins {
    id("aphrodite.android.library")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

// The CMake/NDK build for the on-device inference engine (llama.cpp) is wired up in the
// Cookbook phase, where the vendored sources and JNI bridge land.

dependencies {
    api(project(":core:common"))
}
