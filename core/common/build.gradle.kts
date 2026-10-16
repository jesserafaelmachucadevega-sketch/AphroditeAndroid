plugins {
    id("aphrodite.android.library")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    api(libs.androidx.core.ktx)
}
