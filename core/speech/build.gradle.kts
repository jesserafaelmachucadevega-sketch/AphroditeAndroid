plugins {
    id("aphrodite.android.library")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:media"))
}
