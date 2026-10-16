plugins {
    id("aphrodite.android.library")
    id("aphrodite.android.hilt.worker")
}

android {
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
}
