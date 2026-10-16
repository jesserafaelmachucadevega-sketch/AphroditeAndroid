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
    api(project(":core:datastore"))

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.serialization.json)
}
