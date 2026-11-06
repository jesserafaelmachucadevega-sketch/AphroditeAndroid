package com.aphrodite.core.llm.rag

import com.aphrodite.core.database.dao.DocumentDao
import com.aphrodite.core.database.entity.DocumentChunkEntity
import com.aphrodite.core.database.entity.DocumentEntity
import com.aphrodite.core.llm.EndpointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** A retrieved chunk with its similarity. */
data class RetrievedChunk(
    val documentId: String,
    val documentTitle: String,
    val text: String,
    val score: Float,
)

/**
 * Retrieval over the user's own documents.
 *
 * Embeddings come from the configured provider's `/embeddings` endpoint rather than an on-device
 * model: bundling a model would add tens of megabytes to the APK for something the user has
 * already configured a provider for, and a provider's embedding model is better than anything that
 * would fit.
 *
 * Search is exact cosine over every chunk. At a few thousand chunks that is fast enough on a phone
 * and needs no index to maintain; an approximate index is the right answer only once it stops being
 * fast enough, and pretending otherwise early would add a failure mode for nothing.
 */
@Singleton
class RagRepository @Inject constructor(
    private val dao: DocumentDao,
    private val endpoints: EndpointRepository,
    private val client: com.aphrodite.core.network.HttpClientFactory,
) {
    /** Chunks [body] and stores the document with its vectors. */
    suspend fun index(documentId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val document = dao.get(documentId) ?: error("That document no longer exists.")
            val endpoint = endpoints.observeEnabledEndpoints().first().firstOrNull()
                ?: error("No provider is configured to embed with.")
            val key = apiKeyFor(endpoint)
            val chunks = chunk(document.body)
            if (chunks.isEmpty()) error("The document has no text to index.")

            val vectors = embed(chunks, endpoint.baseUrl, key, EMBEDDING_MODEL)
            if (vectors.size != chunks.size) error("The provider returned the wrong number of vectors.")

            val now = System.currentTimeMillis()
            dao.deleteChunks(documentId)
            dao.insertChunks(
                chunks.mapIndexed { index, text ->
                    DocumentChunkEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        ordinal = index,
                        text = text,
                        embedding = vectors[index].toBytes(),
                    )
                },
            )
            dao.recordIndex(documentId, now, chunks.size, EMBEDDING_MODEL, null)
            chunks.size
        }.onFailure { error ->
            dao.recordIndex(documentId, null, 0, null, error.message)
        }
    }

    /** Returns the closest chunks to [query], best first. */
    suspend fun search(query: String, limit: Int = 5): Result<List<RetrievedChunk>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val chunks = dao.allChunks()
                if (chunks.isEmpty()) return@runCatching emptyList()

                val endpoint = endpoints.observeEnabledEndpoints().first().firstOrNull()
                    ?: error("No provider is configured to embed with.")
                val key = apiKeyFor(endpoint)
                val queryVector = embed(listOf(query), endpoint.baseUrl, key, EMBEDDING_MODEL).first()

                val titles = dao.all().associate { it.id to it.title }
                chunks
                    .mapNotNull { chunk ->
                        val vector = chunk.embedding.toFloats() ?: return@mapNotNull null
                        if (vector.size != queryVector.size) return@mapNotNull null
                        RetrievedChunk(
                            documentId = chunk.documentId,
                            documentTitle = titles[chunk.documentId] ?: "Document",
                            text = chunk.text,
                            score = cosine(queryVector, vector),
                        )
                    }
                    .sortedByDescending { it.score }
                    .take(limit)
            }
        }

    /** Resolves the API key for an endpoint row. */
    private suspend fun apiKeyFor(endpoint: com.aphrodite.core.database.entity.ModelEndpointEntity): String? =
        endpoint.apiKeyRef?.let { endpoints.resolve(endpoint.id)?.apiKey }

    // ── Embedding ────────────────────────────────────────────────────────────

    private suspend fun embed(
        inputs: List<String>,
        baseUrl: String,
        apiKey: String?,
        model: String,
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("input", buildJsonArray { inputs.forEach { add(it) } })
        }

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/embeddings")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .apply {
                apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        client.standard.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(
                    if (response.code == 404) {
                        "This provider has no /embeddings endpoint. Pick a provider or model that does."
                    } else {
                        "Embedding failed (HTTP ${response.code}): ${text.take(200)}"
                    },
                )
            }
            val root = JSON.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: error("The provider returned no embeddings.")
            data.map { element ->
                val array = element.jsonObject["embedding"]?.jsonArray
                    ?: error("An embedding was missing its vector.")
                FloatArray(array.size) { index ->
                    array[index].jsonPrimitive.contentOrNull?.toFloatOrNull() ?: 0f
                }
            }
        }
    }

    /** Splits on paragraph boundaries, packing to a target size with an overlap for context. */
    internal fun chunk(text: String, targetChars: Int = 1200, overlap: Int = 200): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (paragraph in paragraphs) {
            if (current.isNotEmpty() && current.length + paragraph.length > targetChars) {
                chunks += current.toString()
                // Overlap keeps a sentence from losing the context that started it.
                val carry = current.takeLast(overlap.coerceAtMost(current.length))
                current.clear()
                current.append(carry)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
        if (current.isNotBlank()) chunks += current.toString()

        return chunks.flatMap { block ->
            if (block.length <= targetChars * 2) {
                listOf(block)
            } else {
                // A single huge paragraph still has to be split somewhere.
                block.chunked(targetChars)
            }
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private fun FloatArray.toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun ByteArray.toFloats(): FloatArray? {
        if (size % 4 != 0) return null
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / 4) { buffer.float }
    }

    private companion object {
        /** Any OpenAI-compatible embedding model. Configurable per provider is a later concern. */
        const val EMBEDDING_MODEL = "text-embedding-3-small"
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
