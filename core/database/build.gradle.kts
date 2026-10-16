plugins {
    id("aphrodite.android.library")
    id("aphrodite.android.room")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
