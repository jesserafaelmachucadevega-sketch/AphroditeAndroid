plugins {
    `kotlin-dsl`
}

group = "com.aphrodite.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// These must be `implementation` (not `compileOnly`): the precompiled script plugins in this
// module apply them by id, so the jars and their plugin descriptors have to be on the
// build-logic runtime classpath as well as the compile classpath.
dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.room.gradle.plugin)
}
