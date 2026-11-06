package com.aphrodite.inference.llama

/**
 * The native llama.cpp entry points.
 *
 * Kept deliberately thin. Everything above this is Kotlin, so the amount of C++ that has to be
 * written and debugged stays as small as possible.
 */
internal object LlamaNative {

    @Volatile
    private var initialised = false

    /** Loaded lazily so a device without arm64 support fails here rather than at class load. */
    val available: Boolean by lazy {
        runCatching {
            System.loadLibrary("aphrodite_llama")
            true
        }.getOrDefault(false)
    }

    /** Idempotent: llama.cpp aborts the process if its backend is initialised twice. */
    fun initialize(): Boolean {
        if (!available) return false
        if (initialised) return true
        initialised = initializeNative()
        return initialised
    }

    fun systemInfo(): String = if (available) systemInfoNative() else ""

    fun version(): String = if (available) versionNative() else ""

    fun shutdown() {
        if (available && initialised) {
            shutdownNative()
            initialised = false
        }
    }

    private external fun initializeNative(): Boolean
    private external fun systemInfoNative(): String
    private external fun versionNative(): String
    private external fun shutdownNative()
}
