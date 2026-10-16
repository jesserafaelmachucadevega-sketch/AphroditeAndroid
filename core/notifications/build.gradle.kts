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
    api(project(":core:designsystem"))

    api(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
}
