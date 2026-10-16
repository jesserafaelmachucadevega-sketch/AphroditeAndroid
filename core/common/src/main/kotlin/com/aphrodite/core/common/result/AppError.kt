package com.aphrodite.core.common.result

/**
 * Every failure Aphrodite can surface, with enough structure for the UI to decide whether to
 * retry, re-authenticate, or explain a configuration problem.
 */
sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** The feature is not available in this build or on this device. */
    class Unavailable(val feature: String, val reason: String) :
        AppError("$feature is unavailable: $reason")

    /** A required credential or configuration value is missing. */
    class NotConfigured(val what: String, val howToFix: String) :
        AppError("$what is not configured. $howToFix")

    /** Network transport failure — safe to retry. */
    class Network(override val cause: Throwable) : AppError("Network request failed", cause)

    /** Host reachable but rejected the request (bad key, bad model, bad payload). */
    class Rejected(val status: Int, val detail: String) :
        AppError("Request rejected ($status): $detail")

    /** The provider or server failed — safe to retry once. */
    class Upstream(val status: Int, val detail: String) :
        AppError("Upstream error ($status): $detail")

    /** Rate limited; [retryAfterMillis] mirrors any Retry-After the server sent. */
    class RateLimited(val retryAfterMillis: Long?, val detail: String) :
        AppError("Rate limited: $detail")

    /** The user cancelled the operation. */
    data object Cancelled : AppError("Cancelled")

    /** Bad input from the user. */
    class Invalid(val what: String) : AppError(what)

    /** Local storage failure. */
    class Storage(override val cause: Throwable) : AppError("Could not access local storage", cause)

    /** A parse failure on data we fetched or stored. */
    class Malformed(val what: String, override val cause: Throwable? = null) :
        AppError("Malformed $what", cause)

    /** Catch-all for defects; carries the original throwable for logging. */
    class Unexpected(override val cause: Throwable) :
        AppError(cause.message ?: "Unexpected error", cause) {
        constructor(message: String) : this(IllegalStateException(message))
    }
}
