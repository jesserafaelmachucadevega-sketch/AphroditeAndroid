plugins {
    id("aphrodite.android.library")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

android {
    buildFeatures {
        buildConfig = false
    }

    ndkVersion = "27.2.12479018"

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            // arm64 only. Every modern device is arm64, and each additional ABI roughly doubles
            // native build time and adds a full copy of the libraries to the APK.
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            ndk {
                debugSymbolLevel = "none"
            }
        }
    }
}
