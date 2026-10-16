plugins {
    id("aphrodite.android.library")
    id("aphrodite.android.hilt")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api(project(":core:common"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.runtime)
}
