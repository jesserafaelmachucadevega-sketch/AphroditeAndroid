import com.aphrodite.buildlogic.catalog
import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = catalog.findLibrary("compose-bom").get()
    add("implementation", platform(composeBom))
    add("androidTestImplementation", platform(composeBom))

    add("implementation", catalog.findLibrary("compose-runtime").get())
    add("implementation", catalog.findLibrary("compose-ui").get())
    add("implementation", catalog.findLibrary("compose-ui-graphics").get())
    add("implementation", catalog.findLibrary("compose-foundation").get())
    add("implementation", catalog.findLibrary("compose-material3").get())
    add("implementation", catalog.findLibrary("compose-ui-tooling-preview").get())

    add("debugImplementation", catalog.findLibrary("compose-ui-tooling").get())
    add("debugImplementation", catalog.findLibrary("compose-ui-test-manifest").get())

    add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
}
