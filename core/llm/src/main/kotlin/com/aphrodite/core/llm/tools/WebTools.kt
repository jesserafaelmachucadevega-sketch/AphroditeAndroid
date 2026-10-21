package com.aphrodite.core.llm.tools

import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.llm.agent.bounded
import com.aphrodite.core.llm.agent.optionalInt
import com.aphrodite.core.llm.agent.requireString
import com.aphrodite.core.network.HttpClientFactory
import com.aphrodite.core.security.SecretStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.InetAddress
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web search.
 *
 * Brave is used when a key is configured because its results are substantially better; DuckDuckGo's
 * HTML endpoint is the fallback so search works with no setup at all. Requiring a key before the
 * first tool works would mean most people never get a working tool.
 */
@Singleton
class WebSearchService @Inject constructor(
    private val httpFactory: HttpClientFactory,
    private val secretStore: SecretStore,
) {
    data class Hit(val title: String, val url: String, val snippet: String)

    suspend fun search(query: String, limit: Int = 6): List<Hit> {
        val braveKey = secretStore.get(BRAVE_KEY_REF)
        if (!braveKey.isNullOrBlank()) {
            runCatching { brave(query, braveKey, limit) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return duckDuckGo(query, limit)
    }

    private suspend fun brave(query: String, key: String, limit: Int): List<Hit> {
        val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", limit.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", key)
            .get()
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            httpFactory.standard.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext emptyList()
                val root = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: return@withContext emptyList()
                val results = root["web"]?.jsonObject?.get("results") as? kotlinx.serialization.json.JsonArray
                    ?: return@withContext emptyList()
                results.mapNotNull { element ->
                    val item = element.jsonObject
                    val link = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    Hit(
                        title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = link,
                        snippet = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }.take(limit)
            }
        }
    }

    private suspend fun duckDuckGo(query: String, limit: Int): List<Hit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            // DDG serves a stripped page to unknown agents.
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/html")
            .get()
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            httpFactory.standard.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string().orEmpty()
                parseDuckDuckGo(html, limit)
            }
        }
    }

    /** Pulls result anchors and snippets out of the DDG HTML page. */
    internal fun parseDuckDuckGo(html: String, limit: Int): List<Hit> {
        val linkPattern = Regex(
            """<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val snippetPattern = Regex(
            """class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

        val snippets = snippetPattern.findAll(html).map { stripHtml(it.groupValues[1]) }.toList()

        return linkPattern.findAll(html).mapIndexedNotNull { index, match ->
            val href = decodeRedirect(match.groupValues[1])
            if (href.isBlank()) return@mapIndexedNotNull null
            Hit(
                title = stripHtml(match.groupValues[2]),
                url = href,
                snippet = snippets.getOrElse(index) { "" },
            )
        }.take(limit).toList()
    }

    /** DDG wraps outbound links in a redirect; the real target is in the `uddg` parameter. */
    private fun decodeRedirect(href: String): String {
        val normalised = if (href.startsWith("//")) "https:$href" else href
        val url = normalised.toHttpUrlOrNull() ?: return normalised
        return url.queryParameter("uddg") ?: normalised
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val BRAVE_KEY_REF = "search:brave"
        val JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** Lets the model look up current information it was never trained on. */
class WebSearchTool @Inject constructor(
    private val service: WebSearchService,
) : Tool {

    override val name = "web_search"
    override val description = "Search the public web and return ranked results with titles, URLs and snippets."
    override val whenToUse =
        "Use whenever the answer depends on current events, recent releases, prices, versions or " +
            "anything that could have changed since training. Look it up rather than recalling it."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "The search query.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "How many results to return, 1 to 10. Defaults to 6.")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("query")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val query = arguments.requireString("query")
            ?: return ToolOutcome.Failure(
                "No query was provided.",
                "Call web_search with a \"query\" string.",
            )

        val limit = (arguments.optionalInt("limit") ?: 6).coerceIn(1, 10)
        val hits = runCatching { service.search(query, limit) }.getOrElse { error ->
            return ToolOutcome.Failure(
                "The search request failed: ${error.message ?: "unknown error"}",
                "Try a shorter or differently worded query.",
            )
        }

        if (hits.isEmpty()) {
            return ToolOutcome.Success("No results found for \"$query\". Try different terms.")
        }

        return ToolOutcome.Success(
            buildString {
                appendLine("Results for \"$query\":")
                hits.forEachIndexed { index, hit ->
                    appendLine()
                    appendLine("${index + 1}. ${hit.title}")
                    appendLine(hit.url)
                    if (hit.snippet.isNotBlank()) appendLine(hit.snippet)
                }
            }.trim().bounded(),
        )
    }
}

/**
 * Reads a single page as text.
 *
 * Refuses private and loopback addresses: the model chooses the URL, and without this an
 * injected instruction could make it fetch the router's admin page or a local service.
 */
class FetchUrlTool @Inject constructor(
    private val httpFactory: HttpClientFactory,
) : Tool {

    override val name = "fetch_url"
    override val description = "Fetch a web page and return its readable text, with scripts and markup removed."
    override val whenToUse =
        "Use after web_search when a snippet is not enough, or when given a specific URL to read. " +
            "Prefer it over guessing what a page says."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "The absolute http or https URL to read.")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("url")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val url = arguments.requireString("url")
            ?: return ToolOutcome.Failure("No URL was provided.", "Call fetch_url with a \"url\" string.")

        val parsed = url.toHttpUrlOrNull()
            ?: return ToolOutcome.Failure(
                "\"$url\" is not a valid absolute URL.",
                "Include the scheme, for example https://example.com/page.",
            )

        if (parsed.scheme !in setOf("http", "https")) {
            return ToolOutcome.Failure("Only http and https URLs can be fetched.", retryable = false)
        }

        if (isPrivateHost(parsed.host)) {
            return ToolOutcome.Failure(
                "Refusing to fetch $url because it points at a private or local address.",
                "Only public pages can be read.",
                retryable = false,
            )
        }

        val request = Request.Builder()
            .url(parsed)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,text/plain")
            .get()
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                httpFactory.standard.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use ToolOutcome.Failure(
                            "The page returned HTTP ${response.code}.",
                            "Try a different source or check the URL.",
                        ) as ToolOutcome
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    val body = response.body?.string().orEmpty()
                    val text = if (contentType.contains("html", ignoreCase = true)) {
                        htmlToText(body)
                    } else {
                        body
                    }
                    if (text.isBlank()) {
                        ToolOutcome.Failure(
                            "The page had no readable text.",
                            "It may be rendered client-side; try a different source.",
                        )
                    } else {
                        ToolOutcome.Success(text.bounded())
                    }
                }
            }.getOrElse { error ->
                ToolOutcome.Failure(
                    "Fetching failed: ${error.message ?: "unknown error"}",
                    "Check the URL is reachable and try again.",
                )
            }
        }
    }

    internal fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        .replace(Regex("(?is)<noscript[^>]*>.*?</noscript>"), " ")
        .replace(Regex("(?is)<(nav|footer|header|aside)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun isPrivateHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        val literal = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return literal.isLoopbackAddress ||
            literal.isSiteLocalAddress ||
            literal.isLinkLocalAddress ||
            literal.isAnyLocalAddress ||
            literal.isMulticastAddress
    }
}
