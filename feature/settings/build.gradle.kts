plugins {
    id("aphrodite.android.library")
    id("aphrodite.android.library.compose")
    id("aphrodite.android.hilt")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:adaptive"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:llm"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:speech"))
}
