package com.aphrodite.inference.llama

/**
 * What a GGUF file actually says about itself.
 *
 * Read from the model, not from its file name. Guessing from the name is convenient but wrong often
 * enough to matter: renames, re-uploads and quantisation-only differences all produce names that
 * disagree with the file.
 */
data class LlamaModelInfo(
    val description: String,
    val parameterCount: Long,
    val sizeBytes: Long,
    /** The context the model was trained for. Not what it will be run at, but the ceiling. */
    val trainedContextLength: Int,
    val architecture: String?,
    val name: String?,
)

/**
 * Reads a GGUF file's metadata.
 *
 * Loads with the vocabulary only, so describing a seven-gigabyte model costs the same as describing
 * a small one — the weights are never allocated.
 */
object LlamaInspector {

    /** Returns null when the file is not a readable GGUF model. */
    fun inspect(path: String): LlamaModelInfo? {
        if (!LlamaNative.initialize()) return null

        val handle = LlamaNative.loadModel(path, vocabOnly = true)
        if (handle == 0L) return null

        return try {
            LlamaModelInfo(
                description = LlamaNative.modelDescription(handle),
                parameterCount = LlamaNative.modelParameters(handle),
                sizeBytes = LlamaNative.modelSize(handle),
                trainedContextLength = LlamaNative.modelContextLength(handle),
                architecture = LlamaNative.modelMetadata(handle, "general.architecture"),
                name = LlamaNative.modelMetadata(handle, "general.name"),
            )
        } finally {
            LlamaNative.freeModel(handle)
        }
    }

    val isAvailable: Boolean get() = LlamaNative.available

    /** What llama.cpp reports about the device's CPU and backends. Useful when a model will not load. */
    fun systemInfo(): String = LlamaNative.systemInfo()
}
