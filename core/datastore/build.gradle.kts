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
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
