import com.aphrodite.buildlogic.catalog

/**
 * Hilt, without WorkManager integration.
 *
 * Deliberately does not pull in `androidx.hilt:hilt-work`: that artifact pins
 * `androidx.work:work-runtime` at 2.3.4, a 2020 build that predates the `FLAG_IMMUTABLE`
 * requirement and therefore crashes the process on Android 12+ the moment WorkManager
 * initialises. Because WorkManager's initialiser runs on every app start, a module that merely
 * has the artifact on its classpath takes the whole app down.
 *
 * Modules that genuinely declare `@HiltWorker` classes should apply
 * `aphrodite.android.hilt.worker` instead, which adds the integration together with a current
 * WorkManager version that overrides the ancient pin.
 */
plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    add("implementation", catalog.findLibrary("hilt-android").get())
    add("ksp", catalog.findLibrary("hilt-compiler").get())
    add("implementation", catalog.findLibrary("hilt-navigation-compose").get())
}
