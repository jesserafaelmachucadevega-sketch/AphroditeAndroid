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
    api(project(":core:datastore"))

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.kotlinx.serialization.json)
}
