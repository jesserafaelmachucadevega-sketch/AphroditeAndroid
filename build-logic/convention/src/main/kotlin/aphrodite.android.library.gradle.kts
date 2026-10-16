import com.aphrodite.buildlogic.catalog
import com.aphrodite.buildlogic.defaultNamespace
import com.aphrodite.buildlogic.versionInt
import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    namespace = defaultNamespace
    compileSdk = catalog.versionInt("compileSdk")

    defaultConfig {
        minSdk = catalog.versionInt("minSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkDependencies = true
    }
}

dependencies {
    add("coreLibraryDesugaring", catalog.findLibrary("desugar-jdk-libs").get())

    add("implementation", catalog.findLibrary("kotlinx-coroutines-core").get())
    add("implementation", catalog.findLibrary("kotlinx-coroutines-android").get())

    add("testImplementation", catalog.findLibrary("junit").get())
    add("testImplementation", catalog.findLibrary("kotlin-test").get())
    add("testImplementation", catalog.findLibrary("coroutines-test").get())
    add("testImplementation", catalog.findLibrary("turbine").get())
}
