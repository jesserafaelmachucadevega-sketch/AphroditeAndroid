import com.aphrodite.buildlogic.catalog

/**
 * Hilt plus WorkManager integration, for modules that declare `@HiltWorker` classes.
 *
 * Apply this instead of `aphrodite.android.hilt`. It adds the worker integration and, crucially,
 * pins `androidx.work:work-runtime` explicitly: `androidx.hilt:hilt-work` requests 2.3.4, and
 * that version creates a PendingIntent without `FLAG_IMMUTABLE`, which throws on Android 12+ as
 * soon as WorkManager initialises.
 */
plugins {
    id("aphrodite.android.hilt")
}

dependencies {
    add("api", catalog.findLibrary("androidx-work-runtime").get())
    add("implementation", catalog.findLibrary("hilt-work").get())
    add("ksp", catalog.findLibrary("hilt-compiler-androidx").get())
}
