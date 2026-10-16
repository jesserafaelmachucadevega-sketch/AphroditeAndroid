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
    api(project(":core:designsystem"))

    api(libs.jetbrains.markdown)
    implementation(libs.highlights)
}
