plugins {
    id("aphrodite.android.library")
    id("aphrodite.android.library.compose")
    id("aphrodite.android.hilt")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:adaptive"))
    implementation(project(":core:database"))
    implementation(project(":core:llm"))
    implementation(project(":core:media"))
    implementation(libs.coil.network.okhttp)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.coil.compose)
}
