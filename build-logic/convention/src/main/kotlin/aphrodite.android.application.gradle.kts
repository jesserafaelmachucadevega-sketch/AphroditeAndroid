import com.aphrodite.buildlogic.catalog
import com.aphrodite.buildlogic.versionInt
import com.aphrodite.buildlogic.versionString
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
    namespace = "com.aphrodite.app"
    compileSdk = catalog.versionInt("compileSdk")

    defaultConfig {
        applicationId = "com.aphrodite"
        minSdk = catalog.versionInt("minSdk")
        targetSdk = catalog.versionInt("targetSdk")
        versionCode = catalog.versionInt("appVersionCode")
        versionName = catalog.versionString("appVersionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    add("coreLibraryDesugaring", catalog.findLibrary("desugar-jdk-libs").get())
}
