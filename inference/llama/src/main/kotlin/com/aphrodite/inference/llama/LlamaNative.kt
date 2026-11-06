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

    /**
     * Loads a model and returns an opaque handle, or 0 on failure.
     *
     * [vocabOnly] reads the header and vocabulary without allocating the weights, which is what
     * makes inspecting a large model cheap.
     */
    fun loadModel(path: String, vocabOnly: Boolean): Long =
        if (available && initialised) loadModelNative(path, vocabOnly) else 0L

    fun freeModel(handle: Long) {
        if (available && handle != 0L) freeModelNative(handle)
    }

    fun modelDescription(handle: Long): String =
        if (available && handle != 0L) modelDescriptionNative(handle) else ""

    fun modelParameters(handle: Long): Long =
        if (available && handle != 0L) modelParametersNative(handle) else 0L

    fun modelSize(handle: Long): Long =
        if (available && handle != 0L) modelSizeNative(handle) else 0L

    fun modelContextLength(handle: Long): Int =
        if (available && handle != 0L) modelContextLengthNative(handle) else 0

    /** Null when the file does not carry that key, which is normal rather than an error. */
    fun modelMetadata(handle: Long, key: String): String? =
        if (available && handle != 0L) modelMetadataNative(handle, key) else null

    fun createContext(modelHandle: Long, contextSize: Int, threads: Int): Long =
        if (available && modelHandle != 0L) createContextNative(modelHandle, contextSize, threads) else 0L

    fun freeContext(handle: Long) {
        if (available && handle != 0L) freeContextNative(handle)
    }

    fun contextSize(handle: Long): Int =
        if (available && handle != 0L) contextSizeNative(handle) else 0

    fun generate(
        contextHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        listener: TokenListener,
    ): String = if (available && contextHandle != 0L) {
        generateNative(contextHandle, prompt, maxTokens, temperature, topP, listener)
    } else {
        ""
    }

    private external fun initializeNative(): Boolean
    private external fun createContextNative(modelHandle: Long, contextSize: Int, threads: Int): Long
    private external fun freeContextNative(handle: Long)
    private external fun contextSizeNative(handle: Long): Int
    private external fun generateNative(
        contextHandle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        listener: TokenListener,
    ): String
    private external fun loadModelNative(path: String, vocabOnly: Boolean): Long
    private external fun freeModelNative(handle: Long)
    private external fun modelDescriptionNative(handle: Long): String
    private external fun modelParametersNative(handle: Long): Long
    private external fun modelSizeNative(handle: Long): Long
    private external fun modelContextLengthNative(handle: Long): Int
    private external fun modelMetadataNative(handle: Long, key: String): String?
    private external fun systemInfoNative(): String
    private external fun versionNative(): String
    private external fun shutdownNative()
}
