import com.aphrodite.buildlogic.catalog

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    add("implementation", catalog.findLibrary("hilt-android").get())
    add("ksp", catalog.findLibrary("hilt-compiler").get())
    add("implementation", catalog.findLibrary("hilt-navigation-compose").get())
    add("implementation", catalog.findLibrary("hilt-work").get())
    add("ksp", catalog.findLibrary("hilt-compiler-androidx").get())
}
