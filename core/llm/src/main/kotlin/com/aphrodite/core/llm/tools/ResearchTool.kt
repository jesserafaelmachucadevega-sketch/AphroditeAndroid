package com.aphrodite.core.llm.tools

import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.llm.agent.bounded
import com.aphrodite.core.llm.agent.optionalInt
import com.aphrodite.core.llm.agent.requireString
import com.aphrodite.core.network.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import java.net.URI
import javax.inject.Inject

/**
 * Reads several sources at once and hands back what they actually say.
 *
 * The difference from calling the search tool repeatedly is that the model gets source text, not
 * snippets. Snippets are search-engine summaries, so an answer built from them is one step removed
 * from the material and cites pages the model never read. Here each result is fetched and quoted,
 * which is what makes a citation mean something.
 *
 * Pages are fetched concurrently. Sequentially, five sources at a few seconds each is long enough
 * that a user assumes the app has hung.
 */
class ResearchTool @Inject constructor(
    private val search: WebSearchService,
    private val httpFactory: HttpClientFactory,
) : Tool {

    override val name = "research"
    override val description =
        "Search the web and read the top sources in full, returning extracts with their URLs."

    override val whenToUse =
        "When a question needs outside information and one page will not answer it — comparisons, " +
            "current events, anything where several sources should agree. Prefer this over repeated " +
            "web_search calls when you need substance rather than links. For the user's own material, " +
            "use search_documents instead. Cite the numbered sources in your answer."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "What to research.")
            }
            putJsonObject("sources") {
                put("type", "integer")
                put("description", "How many sources to read, 2 to 8. Defaults to 5.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val query = arguments.requireString("query")?.trim()
            ?: return ToolOutcome.Failure("No query was provided.", "Call research with a \"query\" string.")

        val count = (arguments.optionalInt("sources") ?: 5).coerceIn(2, 8)

        val hits = runCatching { search.search(query, limit = count + 2) }
            .getOrElse { error ->
                return ToolOutcome.Failure(
                    error.message ?: "Searching failed.",
                    "Tell the user the search failed rather than answering from memory.",
                )
            }

        if (hits.isEmpty()) {
            return ToolOutcome.Success("No search results for \"$query\".")
        }

        // A couple extra are searched than requested, since some pages fail to load.
        val pages = coroutineScope {
            hits.take(count + 2)
                .map { hit ->
                    async(Dispatchers.IO) {
                        if (!isPublicHost(hit.url)) return@async null
                        val text = fetchText(hit.url) ?: return@async null
                        if (text.length < MIN_USEFUL) return@async null
                        hit to text
                    }
                }
                .awaitAll()
                .filterNotNull()
                .take(count)
        }

        if (pages.isEmpty()) {
            return ToolOutcome.Failure(
                "Found ${hits.size} results but could not read any of them.",
                "Report that the sources could not be reached. Do not answer from memory.",
            )
        }

        return ToolOutcome.Success(
            buildString {
                appendLine("Read ${pages.size} sources for \"$query\".")
                appendLine()
                pages.forEachIndexed { index, (hit, text) ->
                    appendLine("[${index + 1}] ${hit.title}")
                    appendLine(hit.url)
                    appendLine(text.take(PER_SOURCE_CHARS))
                    appendLine()
                }
                appendLine("Cite these as [1], [2] and so on. If a source does not support the claim, say so.")
            }.trim().bounded(),
        )
    }

    /**
     * Reads a page and reduces it to text.
     *
     * A page that will not load is not an error worth surfacing — it just means this source is not
     * part of the answer, and the caller collects whatever did load.
     */
    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()
                httpFactory.standard.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val contentType = response.header("Content-Type").orEmpty()
                    if (contentType.isNotBlank() && !contentType.contains("text", ignoreCase = true) &&
                        !contentType.contains("html", ignoreCase = true) &&
                        !contentType.contains("xml", ignoreCase = true)
                    ) {
                        // A PDF or binary is not readable here; better to skip than to store noise.
                        return@use null
                    }
                    response.body?.string()?.let(::toText)
                }
            }.getOrNull()
        }
    }

    private fun toText(html: String): String = html
        .replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?is)<!--.*?-->"), " ")
        .replace(Regex("(?is)<(p|div|br|li|h[1-6]|tr)[^>]*>"), "\n")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .let(::unescape)
        .replace(Regex("[ \\t\\u00A0]+"), " ")
        .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()

    /**
     * Minimal entity decoding.
     *
     * A full HTML parser is not worth the dependency for text that gets truncated and paraphrased
     * anyway; these are the entities that actually appear in prose.
     */
    private fun unescape(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace(Regex("&#(\\d{2,5});")) { match ->
            match.groupValues[1].toIntOrNull()?.let { code -> String(Character.toChars(code)) } ?: ""
        }

    /**
     * Refuses private and loopback hosts.
     *
     * Search results are attacker-influenced content. Without this, a crafted result could point the
     * tool at the router or a service on the local network and return its response into the answer.
     */
    private fun isPublicHost(url: String): Boolean = runCatching {
        val host = URI(url).host?.lowercase() ?: return@runCatching false
        when {
            host == "localhost" || host.endsWith(".localhost") -> false
            host.endsWith(".local") -> false
            host.startsWith("127.") || host == "::1" -> false
            host.startsWith("10.") -> false
            host.startsWith("192.168.") -> false
            host.startsWith("169.254.") -> false
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host) -> false
            !host.contains('.') -> false
            else -> true
        }
    }.getOrDefault(false)

    private companion object {
        const val FETCH_TIMEOUT_MS = 12_000L

        /** A page this short is a redirect or a paywall, not a source. */
        const val MIN_USEFUL = 400

        /** Enough for the model to reason over without filling the whole context window. */
        const val PER_SOURCE_CHARS = 4000

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
