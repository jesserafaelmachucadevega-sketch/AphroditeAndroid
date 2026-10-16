package com.aphrodite.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** One server-sent event: an optional named event plus its data payload. */
data class SseFrame(val event: String?, val data: String)

/** Raised when a stream fails after the response had already started. */
class SseException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads `text/event-stream` responses as a flow of frames.
 *
 * Implements the parts of the event-stream format the providers actually use: `data:` and
 * `event:` fields, comment lines starting with `:` (which providers send as keep-alives), and
 * dispatching on a blank line. A `data: [DONE]` sentinel simply arrives as a frame whose data is
 * `[DONE]`, leaving the decision to stop with the caller.
 */
class SseClient(private val client: OkHttpClient) {

    /**
     * Opens [request] and emits frames until the server closes the stream.
     *
     * The flow is cancellation-aware: collecting is stopped by cancelling the scope, which
     * cancels the underlying call.
     */
    fun stream(request: Request): Flow<SseFrame> = callbackFlow {
        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isActive) close(SseException(e.message ?: "Stream failed", e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val body = runCatching { res.body?.string().orEmpty() }.getOrDefault("")
                        close(SseException("HTTP ${res.code}: ${body.take(500)}"))
                        return
                    }
                    val source = res.body?.source()
                    if (source == null) {
                        close(SseException("Stream had no body"))
                        return
                    }

                    var eventName: String? = null
                    val data = StringBuilder()

                    try {
                        while (isActive && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> {
                                    // A blank line terminates the event.
                                    if (data.isNotEmpty()) {
                                        trySend(SseFrame(eventName, data.toString().trimEnd('\n')))
                                    }
                                    eventName = null
                                    data.setLength(0)
                                }

                                line.startsWith(":") -> Unit // keep-alive comment

                                line.startsWith("event:") ->
                                    eventName = line.removePrefix("event:").trim()

                                line.startsWith("data:") -> {
                                    val value = line.removePrefix("data:").let {
                                        if (it.startsWith(" ")) it.substring(1) else it
                                    }
                                    if (data.isNotEmpty()) data.append('\n')
                                    data.append(value)
                                }

                                // `id:`, `retry:` and anything else are not needed here.
                                else -> Unit
                            }
                        }
                        // Flush a trailing event that was not followed by a blank line.
                        if (data.isNotEmpty() && isActive) {
                            trySend(SseFrame(eventName, data.toString().trimEnd('\n')))
                        }
                        close()
                    } catch (e: IOException) {
                        close(SseException(e.message ?: "Stream interrupted", e))
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)
}
