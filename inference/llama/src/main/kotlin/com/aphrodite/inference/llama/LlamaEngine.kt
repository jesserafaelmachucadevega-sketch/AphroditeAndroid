package com.aphrodite.inference.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.coroutineContext

/** Called for each generated piece as it arrives, so the answer can be shown while it is written. */
fun interface TokenListener {
    fun onToken(text: String)
}

/**
 * Runs a GGUF model on the device.
 *
 * Kept as a Closeable because the memory involved is not small: a loaded model and its context can
 * hold gigabytes, and waiting for the garbage collector to get round to it will kill the process
 * first. Nothing here is thread-safe and nothing pretends to be — one engine drives one
 * conversation.
 */
class LlamaEngine : Closeable {

    private var modelHandle: Long = 0L
    private var contextHandle: Long = 0L

    val isLoaded: Boolean get() = modelHandle != 0L && contextHandle != 0L

    /**
     * Loads [path] and creates a context of [contextSize] tokens.
     *
     * Both happen together because a model without a context cannot generate, and a context without
     * a model is meaningless. Leaving them separate invites a caller to have one and not the other.
     */
    suspend fun load(
        path: String,
        contextSize: Int = DEFAULT_CONTEXT,
        threads: Int = defaultThreads(),
    ): Result<LlamaModelInfo> = withContext(Dispatchers.IO) {
        close()
        runCatching {
            if (!LlamaNative.initialize()) {
                error("The native engine is not available on this device.")
            }

            val model = LlamaNative.loadModel(path, vocabOnly = false)
            if (model == 0L) {
                error("The model could not be loaded. It may be corrupt or need more memory than is available.")
            }

            val context = LlamaNative.createContext(model, contextSize, threads)
            if (context == 0L) {
                LlamaNative.freeModel(model)
                error("The context could not be created. Try a smaller context size.")
            }

            modelHandle = model
            contextHandle = context

            LlamaModelInfo(
                description = LlamaNative.modelDescription(model),
                parameterCount = LlamaNative.modelParameters(model),
                sizeBytes = LlamaNative.modelSize(model),
                trainedContextLength = LlamaNative.modelContextLength(model),
                architecture = LlamaNative.modelMetadata(model, "general.architecture"),
                name = LlamaNative.modelMetadata(model, "general.name"),
            )
        }
    }

    /**
     * Generates a reply, calling [onToken] as it is written.
     *
     * Runs on [Dispatchers.IO] rather than the default pool: this blocks a thread for the whole
     * generation, and occupying a default-pool thread for that long starves everything else queued
     * behind it.
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        onToken: TokenListener? = null,
    ): String = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext ""

        // Cancellation arrives as an interrupt, which the native loop checks between tokens.
        // Without the ensureActive here a cancelled turn would still run to completion.
        coroutineContext.ensureActive()

        LlamaNative.generate(
            contextHandle,
            prompt,
            maxTokens,
            temperature,
            topP,
            onToken ?: TokenListener { },
        )
    }

    override fun close() {
        if (contextHandle != 0L) {
            LlamaNative.freeContext(contextHandle)
            contextHandle = 0L
        }
        if (modelHandle != 0L) {
            LlamaNative.freeModel(modelHandle)
            modelHandle = 0L
        }
    }

    private companion object {
        const val DEFAULT_CONTEXT = 4096

        /**
         * One fewer than the core count, floored at two.
         *
         * Leaving a core free is what stops the interface stuttering during generation. Fully
         * saturating the CPU produces a faster answer that is worse to wait for.
         */
        fun defaultThreads(): Int =
            (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)
    }
}
