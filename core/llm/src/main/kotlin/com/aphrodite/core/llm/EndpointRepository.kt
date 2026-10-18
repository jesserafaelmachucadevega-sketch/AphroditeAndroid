package com.aphrodite.core.llm

import com.aphrodite.core.common.result.AppError
import com.aphrodite.core.common.result.AppResult
import com.aphrodite.core.database.dao.ModelDao
import com.aphrodite.core.database.dao.ModelEndpointDao
import com.aphrodite.core.database.entity.ModelEndpointEntity
import com.aphrodite.core.database.entity.ModelEntity
import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.core.llm.model.ModelDescriptor
import com.aphrodite.core.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** An endpoint together with its decrypted key, for callers that need to make a request. */
data class ResolvedEndpoint(
    val endpoint: ModelEndpointEntity,
    val apiKey: String?,
)

/**
 * Owns model endpoints and the models discovered on them.
 *
 * API keys never touch the database: the row stores a reference into the encrypted secret store,
 * so an export or a database dump cannot leak a credential.
 */
@Singleton
class EndpointRepository @Inject constructor(
    private val endpointDao: ModelEndpointDao,
    private val modelDao: ModelDao,
    private val secretStore: SecretStore,
    private val client: LlmClient,
) {
    fun observeEndpoints(): Flow<List<ModelEndpointEntity>> = endpointDao.observeAll()
    fun observeEnabledEndpoints(): Flow<List<ModelEndpointEntity>> = endpointDao.observeEnabled()

    fun observeModels(endpointId: String): Flow<List<ModelEntity>> = modelDao.observeForEndpoint(endpointId)

    /** Every discovered model across all endpoints, for the chat model picker. */
    fun observeAllModels(): Flow<List<ModelEntity>> = modelDao.observeAll()

    fun observeFavourites(): Flow<List<ModelEntity>> = modelDao.observeFavourites()

    suspend fun get(endpointId: String): ModelEndpointEntity? = endpointDao.getById(endpointId)

    /** Resolves an endpoint and decrypts its key in one step. */
    suspend fun resolve(endpointId: String): ResolvedEndpoint? {
        val endpoint = endpointDao.getById(endpointId) ?: return null
        val key = endpoint.apiKeyRef?.let { secretStore.get(it) }
        return ResolvedEndpoint(endpoint, key)
    }

    /**
     * Creates or updates an endpoint.
     *
     * Passing a non-null [apiKey] replaces the stored secret; passing null leaves it alone, which
     * is what the edit screen needs so saving without retyping the key does not wipe it.
     */
    suspend fun save(
        id: String?,
        name: String,
        kind: ProviderKind,
        baseUrl: String,
        apiKey: String?,
        supportsTools: Boolean,
        supportsVision: Boolean,
        supportsReasoning: Boolean,
        maxContext: Int?,
    ): AppResult<ModelEndpointEntity> {
        if (name.isBlank()) return AppResult.Failure(AppError.Invalid("Give the endpoint a name"))
        if (baseUrl.isBlank()) return AppResult.Failure(AppError.Invalid("Enter the base URL"))

        val existing = id?.let { endpointDao.getById(it) }
        val endpointId = existing?.id ?: UUID.randomUUID().toString()

        var keyRef = existing?.apiKeyRef
        if (apiKey != null) {
            if (apiKey.isBlank()) {
                keyRef?.let { secretStore.remove(it) }
                keyRef = null
            } else {
                val reference = keyRef ?: "endpoint:$endpointId"
                secretStore.put(reference, apiKey.trim())
                keyRef = reference
            }
        }

        val endpoint = ModelEndpointEntity(
            id = endpointId,
            name = name.trim(),
            kind = kind,
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKeyRef = keyRef,
            supportsTools = supportsTools,
            supportsVision = supportsVision,
            supportsReasoning = supportsReasoning,
            maxContext = maxContext,
            enabled = existing?.enabled ?: true,
            sortOrder = existing?.sortOrder ?: 0,
            lastProbedAt = existing?.lastProbedAt,
            lastError = existing?.lastError,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        endpointDao.upsert(endpoint)
        return AppResult.Success(endpoint)
    }

    suspend fun delete(endpointId: String) {
        endpointDao.getById(endpointId)?.apiKeyRef?.let { secretStore.remove(it) }
        modelDao.deleteForEndpoint(endpointId)
        endpointDao.delete(endpointId)
    }

    suspend fun setEnabled(endpointId: String, enabled: Boolean) = endpointDao.setEnabled(endpointId, enabled)

    /**
     * Fetches the model list from an endpoint and stores it.
     *
     * The endpoint row records the outcome either way, so the list screen can show why a
     * provider is not working without the user having to read a log.
     */
    suspend fun refreshModels(endpointId: String): AppResult<Int> {
        val resolved = resolve(endpointId)
            ?: return AppResult.Failure(AppError.Invalid("That endpoint no longer exists"))

        return try {
            val descriptors = client.listModels(
                kind = resolved.endpoint.kind,
                baseUrl = resolved.endpoint.baseUrl,
                apiKey = resolved.apiKey,
            )
            if (descriptors.isEmpty()) {
                endpointDao.recordProbe(endpointId, System.currentTimeMillis(), "The provider returned no models")
                return AppResult.Failure(AppError.Invalid("The provider returned no models"))
            }
            persist(endpointId, descriptors)
            endpointDao.recordProbe(endpointId, System.currentTimeMillis(), null)
            AppResult.Success(descriptors.size)
        } catch (e: com.aphrodite.core.llm.provider.ProviderHttpException) {
            val message = describeHttpFailure(e)
            endpointDao.recordProbe(endpointId, System.currentTimeMillis(), message)
            AppResult.Failure(AppError.Rejected(e.status, message))
        } catch (e: Exception) {
            val message = describeNetworkFailure(e, resolved.endpoint.baseUrl)
            endpointDao.recordProbe(endpointId, System.currentTimeMillis(), message)
            // Carry the specific text through to the UI, not just onto the stored row:
            // AppError.Network's own message is generic and would hide the cause.
            AppResult.Failure(AppError.Unexpected(message))
        }
    }

    private suspend fun persist(endpointId: String, descriptors: List<ModelDescriptor>) {
        val now = System.currentTimeMillis()
        val rows = descriptors.map { descriptor ->
            ModelEntity(
                id = "$endpointId:${descriptor.id}",
                endpointId = endpointId,
                modelId = descriptor.id,
                displayName = descriptor.displayName,
                contextLength = descriptor.contextLength,
                maxOutput = descriptor.maxOutput,
                vision = descriptor.supportsVision ?: false,
                tools = descriptor.supportsTools ?: false,
                reasoning = descriptor.supportsReasoning ?: false,
                inputCostPerMillion = descriptor.inputCostPerMillion,
                outputCostPerMillion = descriptor.outputCostPerMillion,
                isFavourite = false,
                isHidden = false,
                discoveredAt = now,
            )
        }
        // Replacing wholesale would discard favourites, so only the discovery fields are rewritten.
        modelDao.deleteForEndpoint(endpointId)
        modelDao.upsertAll(rows)
    }

    suspend fun setFavourite(modelRowId: String, favourite: Boolean) =
        modelDao.setFavourite(modelRowId, favourite)

    suspend fun setModelHidden(modelRowId: String, hidden: Boolean) =
        modelDao.setHidden(modelRowId, hidden)

    suspend fun modelsFor(endpointId: String): List<ModelEntity> =
        modelDao.observeForEndpoint(endpointId).first()

    /** Turns a provider failure into something worth showing a person. */
    private fun describeHttpFailure(e: com.aphrodite.core.llm.provider.ProviderHttpException): String =
        when (e.status) {
            401, 403 -> "The provider rejected the API key"
            404 -> "The base URL does not look right for this provider"
            429 -> "The provider is rate limiting; try again shortly"
            in 500..599 -> "The provider is having trouble (HTTP ${e.status})"
            else -> "HTTP ${e.status}: ${e.body.take(200)}"
        }

    /**
     * Names the actual network failure.
     *
     * "Could not reach the endpoint" is useless: a wrong hostname, a refused port, a blocked
     * plain-HTTP connection and a rejected certificate all need different fixes, and the user
     * can only act on the specific one.
     */
    private fun describeNetworkFailure(e: Throwable, baseUrl: String): String {
        val host = runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: baseUrl

        return when (e) {
            is java.net.UnknownHostException ->
                "Could not resolve \"$host\". Check the base URL for a typo."

            is java.net.ConnectException ->
                "Connection refused by $host. Is the server running, and is the port right?"

            is java.net.SocketTimeoutException ->
                "$host did not respond in time."

            is javax.net.ssl.SSLException ->
                "The TLS handshake with $host failed. The certificate was rejected."

            is java.net.NoRouteToHostException ->
                "No route to $host. The device may be on a different network."

            is java.net.UnknownServiceException ->
                "Android blocked a plain-HTTP connection to $host."

            else -> e.message?.takeIf { it.isNotBlank() }
                ?: "Could not reach $host (${e::class.simpleName})"
        }
    }
}
