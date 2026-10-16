package com.aphrodite.core.llm.provider

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

/** Shared JSON configuration for every provider adapter. */
internal val JSON: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = false
}

/** Completions endpoints all take JSON with an explicit charset. */
internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** Raised when a provider answers with a non-success status. */
class ProviderHttpException(val status: Int, val body: String) :
    Exception("HTTP $status: $body")
