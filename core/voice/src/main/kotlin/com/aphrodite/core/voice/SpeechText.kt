package com.aphrodite.core.voice

/**
 * Turns a markdown answer into something worth listening to.
 *
 * Reading punctuation aloud is the main way text-to-speech sounds broken. A screen reader or engine
 * will happily announce "asterisk asterisk bold asterisk asterisk" and "hash hash heading", and a
 * code block becomes a minute of punctuation noise. Stripping those is the difference between an
 * answer being listenable and being worse than reading it.
 */
object SpeechText {

    fun forReading(markdown: String): String {
        var text = markdown

        // Fenced code blocks are summarised rather than read. The punctuation is meaningless aloud
        // and a long block would stall the answer while it is spelled out.
        text = text.replace(Regex("(?s)```[^`]*```")) { match ->
            val language = match.value.removePrefix("```").lineSequence().firstOrNull()?.trim()
            if (language.isNullOrBlank()) " (code omitted) " else " (${language} code omitted) "
        }

        text = text
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")   // headings
            .replace(Regex("(?m)^\\s*>\\s?"), "")             // block quotes
            .replace(Regex("(?m)^\\s*[-*+]\\s+"), ", ")       // bullets
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")        // bold
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])"), "$1") // italics
            .replace(Regex("`([^`]+)`"), "$1")                // inline code
            .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1") // links and images
            .replace(Regex("(?m)^\\s*[-|: ]{3,}\\s*$"), "")   // table rules
            .replace(Regex("\\|"), ", ")                      // table cells

        return text
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("\\n"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
