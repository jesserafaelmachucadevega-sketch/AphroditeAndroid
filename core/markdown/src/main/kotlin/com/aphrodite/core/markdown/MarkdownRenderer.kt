package com.aphrodite.core.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.aphrodite.designsystem.theme.AphroditeColors
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * A rendered piece of a Markdown document.
 *
 * Block structure is preserved rather than flattened into one string, because code blocks and
 * tables need different composables from ordinary prose.
 */
sealed interface MarkdownBlock {
    data class Paragraph(val text: AnnotatedString, val plain: String) : MarkdownBlock
    data class Heading(val level: Int, val text: AnnotatedString, val plain: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class BulletList(val items: List<ListItem>) : MarkdownBlock
    data class OrderedList(val start: Int, val items: List<ListItem>) : MarkdownBlock
    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Rule : MarkdownBlock

    /** One list entry; nested lists are flattened one level with their own indent. */
    data class ListItem(val text: AnnotatedString, val plain: String, val depth: Int)
}

/**
 * Parses Markdown into blocks with inline styling already applied.
 *
 * Colours come from the active theme, so a fenced code block picks up the theme's code
 * background and syntax palette rather than a fixed scheme. Links are styled but not clickable
 * here — the caller attaches link handling when it has somewhere to send them.
 */
class MarkdownRenderer(private val colors: AphroditeColors) {

    private val parser = MarkdownParser(GFMFlavourDescriptor())

    fun parse(source: String): List<MarkdownBlock> {
        if (source.isBlank()) return emptyList()
        val root = parser.buildMarkdownTreeFromString(source)
        return root.children.mapNotNull { renderBlock(it, source, depth = 0) }
    }

    // ── Blocks ────────────────────────────────────────────────────────────────

    private fun renderBlock(node: ASTNode, source: String, depth: Int): MarkdownBlock? = when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> {
            val text = inline(node, source)
            MarkdownBlock.Paragraph(text, node.getTextInNode(source).toString())
        }

        MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 ->
            heading(node, source, 1)
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 ->
            heading(node, source, 2)
        MarkdownElementTypes.ATX_3 -> heading(node, source, 3)
        MarkdownElementTypes.ATX_4 -> heading(node, source, 4)
        MarkdownElementTypes.ATX_5 -> heading(node, source, 5)
        MarkdownElementTypes.ATX_6 -> heading(node, source, 6)

        MarkdownElementTypes.CODE_FENCE -> codeFence(node, source)
        MarkdownElementTypes.CODE_BLOCK -> MarkdownBlock.CodeBlock(null, dedent(node.getTextInNode(source).toString()))

        MarkdownElementTypes.UNORDERED_LIST ->
            MarkdownBlock.BulletList(listItems(node, source, depth))

        MarkdownElementTypes.ORDERED_LIST -> {
            val startNumber = ORDERED_START.find(node.getTextInNode(source).toString())
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            MarkdownBlock.OrderedList(startNumber, listItems(node, source, depth))
        }

        MarkdownElementTypes.BLOCK_QUOTE -> {
            val inner = node.children.mapNotNull { renderBlock(it, source, depth) }
            MarkdownBlock.Quote(inner.ifEmpty {
                listOf(MarkdownBlock.Paragraph(inline(node, source), node.getTextInNode(source).toString()))
            })
        }

        MarkdownTokenTypes.HORIZONTAL_RULE -> MarkdownBlock.Rule

        GFMElementTypes.TABLE -> renderTable(node, source)

        else -> {
            // Whitespace and stray tokens between blocks carry no content.
            val raw = node.getTextInNode(source).toString()
            if (raw.isBlank()) null
            else MarkdownBlock.Paragraph(inline(node, source), raw.trim())
        }
    }

    private fun heading(node: ASTNode, source: String, level: Int): MarkdownBlock.Heading {
        val text = inline(node, source)
        return MarkdownBlock.Heading(level, text, text.text)
    }

    private fun codeFence(node: ASTNode, source: String): MarkdownBlock.CodeBlock {
        val info = node.children
            .firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.getTextInNode(source)?.toString()?.trim()
        val body = node.children
            .firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            ?.getTextInNode(source)?.toString()
            ?.trimEnd('\n')
            ?: ""
        return MarkdownBlock.CodeBlock(info?.ifBlank { null }, body)
    }

    private fun listItems(listNode: ASTNode, source: String, depth: Int): List<MarkdownBlock.ListItem> {
        val items = mutableListOf<MarkdownBlock.ListItem>()
        listNode.children.forEach { child ->
            when (child.type) {
                MarkdownElementTypes.LIST_ITEM -> {
                    val nested = child.children.filter {
                        it.type == MarkdownElementTypes.UNORDERED_LIST ||
                            it.type == MarkdownElementTypes.ORDERED_LIST
                    }
                    val contentNodes = child.children.filterNot { it in nested }

                    val text = buildAnnotatedString {
                        contentNodes.forEachIndexed { index, part ->
                            if (index > 0) append(" ")
                            append(inline(part, source))
                        }
                    }.trimmed()
                    items += MarkdownBlock.ListItem(text, text.text, depth)

                    // Nested lists are emitted as further items rather than a tree.
                    nested.forEach { nestedList ->
                        items += listItems(nestedList, source, depth + 1)
                    }
                }
                else -> Unit
            }
        }
        return items
    }

    private fun renderTable(node: ASTNode, source: String): MarkdownBlock.Table? {
        val rows = mutableListOf<List<String>>()

        fun collectRows(n: ASTNode) {
            // The GFM flavour models the header row and body rows as separate element types,
            // both containing CELL tokens.
            if (n.type == GFMElementTypes.HEADER || n.type == GFMElementTypes.ROW) {
                val cells = n.children
                    .filter { it.type == GFMTokenTypes.CELL }
                    .map { cell -> inline(cell, source).text.trim() }
                if (cells.isNotEmpty()) rows += cells
            }
            n.children.forEach(::collectRows)
        }
        collectRows(node)

        if (rows.isEmpty()) return null
        return MarkdownBlock.Table(header = rows.first(), rows = rows.drop(1))
    }

    // ── Inline styling ────────────────────────────────────────────────────────

    private fun inline(node: ASTNode, source: String): AnnotatedString = buildAnnotatedString {
        appendNode(node, source)
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendNode(node: ASTNode, source: String) {
        when (node.type) {
            MarkdownElementTypes.STRONG ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    node.children.forEach { appendNode(it, source) }
                }

            MarkdownElementTypes.EMPH ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    node.children.forEach { appendNode(it, source) }
                }

            GFMElementTypes.STRIKETHROUGH ->
                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                    node.children.forEach { appendNode(it, source) }
                }

            MarkdownElementTypes.CODE_SPAN ->
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = colors.codeBg,
                        color = colors.hlString,
                    ),
                ) {
                    // Drop the surrounding backticks the token carries.
                    append(stripBackticks(node.getTextInNode(source).toString()))
                }

            MarkdownElementTypes.INLINE_LINK, MarkdownElementTypes.FULL_REFERENCE_LINK ->
                withStyle(SpanStyle(color = colors.colorLinkHover, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                    node.children.firstOrNull()?.let { appendNode(it, source) }
                        ?: append(node.getTextInNode(source).toString())
                }

            MarkdownElementTypes.AUTOLINK ->
                withStyle(SpanStyle(color = colors.colorLinkHover)) {
                    append(stripAngleBrackets(node.getTextInNode(source).toString()))
                }

            MarkdownElementTypes.IMAGE ->
                append("[image]")

            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.EOL,
            MarkdownTokenTypes.CODE_FENCE_CONTENT,
            -> append(node.getTextInNode(source).toString())

            else -> {
                // Structural node: recurse, or take its literal text if it has no children.
                if (node.children.isEmpty()) {
                    append(node.getTextInNode(source).toString())
                } else {
                    node.children.forEach { appendNode(it, source) }
                }
            }
        }
    }

    private fun stripBackticks(value: String): String =
        value.removePrefix("`").removeSuffix("`")

    private fun stripAngleBrackets(value: String): String =
        value.removePrefix("<").removeSuffix(">")

    /** Removes the common leading indentation an indented code block carries. */
    private fun dedent(value: String): String {
        val lines = value.trimEnd('\n').lines()
        val indent = lines.filter { it.isNotBlank() }.minOfOrNull { it.takeWhile(Char::isWhitespace).length } ?: 0
        return lines.joinToString("\n") { it.drop(indent) }
    }

    private companion object {
        val ORDERED_START = Regex("""^\s*(\d+)""")
    }
}

/**
 * Trims surrounding whitespace while keeping the span styling intact.
 *
 * `AnnotatedString` implements `CharSequence`, so the ordinary `trim()` would return a plain
 * String and silently drop every span.
 */
private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() }
    return subSequence(start, end + 1)
}
