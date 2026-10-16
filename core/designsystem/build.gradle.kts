plugins {
    id("aphrodite.android.library")
    id("aphrodite.android.library.compose")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.window)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
}
