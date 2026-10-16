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
    api(project(":core:network"))
    api(project(":core:database"))
    api(project(":core:datastore"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
