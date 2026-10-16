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
    implementation(project(":core:markdown"))
    implementation(project(":core:media"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:notifications"))

    implementation(libs.angus.mail)
    implementation(libs.jakarta.mail.api)
    implementation(libs.appauth)
}
