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

    api(libs.okhttp)
    api(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
}
