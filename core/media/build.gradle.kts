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
    api(project(":core:database"))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)
    implementation(libs.androidx.core.ktx)
}
