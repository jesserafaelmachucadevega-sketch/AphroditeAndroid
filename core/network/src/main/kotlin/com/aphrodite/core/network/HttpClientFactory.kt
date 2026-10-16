package com.aphrodite.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the HTTP clients the app talks to model providers with.
 *
 * There are two shapes of client. Ordinary calls get a normal timeout; streaming calls get no
 * read timeout at all, because a model can legitimately think for minutes before emitting its
 * first token and a timeout there would abort a working request.
 */
@Singleton
class HttpClientFactory @Inject constructor() {

    /** For request/response calls: model listing, probes, mail, CalDAV, search. */
    val standard: OkHttpClient by lazy {
        base().build()
    }

    /** For server-sent events and any long-lived stream. */
    val streaming: OkHttpClient by lazy {
        base()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** For large uploads and downloads, where the body transfer dominates the time. */
    val transfer: OkHttpClient by lazy {
        base()
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun base(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
}
