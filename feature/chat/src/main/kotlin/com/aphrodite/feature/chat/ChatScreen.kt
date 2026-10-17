package com.aphrodite.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.ChatMessageEntity
import com.aphrodite.core.database.entity.MessageRole
import com.aphrodite.core.database.entity.SessionEntity
import com.aphrodite.core.markdown.MarkdownBlock
import com.aphrodite.core.markdown.MarkdownRenderer
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSearchField
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * The chat surface.
 *
 * Renders as a session list beside the transcript on wide layouts and as a single pane on
 * compact ones, using the same two-pane treatment as every other list/detail screen.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AphroditeTwoPane(
        showDetail = state.activeSession != null,
        modifier = modifier,
        listWidth = 300.dp,
        list = {
            SessionList(
                state = state,
                onSelect = onSelectSession,
                onNew = onNewSession,
                onDelete = onDeleteSession,
                onSearchChange = onSearchChange,
            )
        },
        emptyDetail = {
            AphroditeEmptyState(
                title = "No conversation selected",
                description = "Start a new chat to begin.",
                icon = AphroditeIcons.Chat,
            )
        },
        detail = {
            Conversation(
                state = state,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onDismissError = onDismissError,
            )
        },
    )
}

@Composable
private fun SessionList(
    state: ChatUiState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chats",
                color = colors.colorSubheader,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Plus,
                contentDescription = "New chat",
                onClick = onNew,
                size = 28.dp,
            )
        }
        Box(Modifier.padding(horizontal = AphroditeSpacing.sm)) {
            AphroditeSearchField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search chats",
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        AphroditeDivider()

        if (state.visibleSessions.isEmpty()) {
            AphroditeEmptyState(
                title = if (state.searchQuery.isBlank()) "No chats yet" else "No matches",
                description = if (state.searchQuery.isBlank()) "Start one to get going." else null,
                icon = AphroditeIcons.Chat,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.visibleSessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        selected = session.id == state.activeSession?.id,
                        onClick = { onSelect(session.id) },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(if (selected) colors.accentPrimary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(start = AphroditeSpacing.sm, end = AphroditeSpacing.xs, top = AphroditeSpacing.sm, bottom = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = AphroditeSpacing.sm),
        ) {
            Text(
                text = session.name,
                color = if (selected) colors.accentPrimary else colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .androidClickable(onClick, Role.Button),
            )
            Text(
                text = when {
                    session.messageCount == 0 -> session.model ?: "No messages"
                    session.messageCount == 1 -> "1 message"
                    else -> "${session.messageCount} messages"
                },
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        AphroditeIconButton(
            icon = AphroditeIcons.Trash,
            contentDescription = "Delete chat",
            onClick = onDelete,
            size = 26.dp,
            tint = colors.colorMuted,
        )
    }
}

@Composable
private fun Conversation(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = aphroditeColors
    val listState = rememberLazyListState()
    val renderer = remember(colors) { MarkdownRenderer(colors) }

    // Keep the newest content in view as the answer streams in.
    LaunchedEffect(state.messages.size, state.streamingText) {
        val target = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.error != null) {
            ErrorBanner(state.error, onDismissError)
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty() && state.streamingText.isEmpty()) {
                AphroditeEmptyState(
                    title = state.activeSession?.name ?: "New chat",
                    description = state.activeSession?.model
                        ?: "Pick a model in Settings, then start typing.",
                    icon = AphroditeIcons.Chat,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AphroditeSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message = message, renderer = renderer)
                    }
                    if (state.streamingText.isNotEmpty() || state.isStreaming) {
                        item(key = "streaming") {
                            StreamingBubble(
                                thinking = state.streamingThinking,
                                text = state.streamingText,
                                renderer = renderer,
                            )
                        }
                    }
                }
            }
        }

        Composer(
            value = state.input,
            onValueChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            isStreaming = state.isStreaming,
            enabled = state.activeSession != null,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.colorError.copy(alpha = 0.14f))
            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AphroditeIcons.AlertCircle,
            contentDescription = null,
            tint = colors.colorError,
            modifier = Modifier.size(14.dp),
        )
        Box(Modifier.size(AphroditeSpacing.sm))
        Text(
            text = message,
            color = colors.colorError,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        AphroditeIconButton(
            icon = AphroditeIcons.Close,
            contentDescription = "Dismiss",
            onClick = onDismiss,
            size = 24.dp,
            tint = colors.colorError,
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessageEntity, renderer: MarkdownRenderer) {
    val colors = aphroditeColors
    val isUser = message.role == MessageRole.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .clip(RoundedCornerShape(AphroditeRadius.md))
                .background(if (isUser) colors.userBubbleBg else colors.aiBubbleBg)
                .border(
                    1.dp,
                    if (isUser) colors.accentPrimary.copy(alpha = 0.35f) else colors.border,
                    RoundedCornerShape(AphroditeRadius.md),
                )
                .padding(AphroditeSpacing.md),
        ) {
            val reasoning = message.thinking
            if (!reasoning.isNullOrBlank()) {
                ThinkingBlock(reasoning)
            }
            if (isUser) {
                Text(
                    text = message.content,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            } else {
                MarkdownContent(text = message.content, renderer = renderer)
            }
            if (message.stopped) {
                Box(Modifier.size(AphroditeSpacing.sm))
                Text(
                    text = "Stopped",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(
    thinking: String,
    text: String,
    renderer: MarkdownRenderer,
) {
    val colors = aphroditeColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.md))
            .background(colors.aiBubbleBg)
            .border(1.dp, colors.border, RoundedCornerShape(AphroditeRadius.md))
            .padding(AphroditeSpacing.md),
    ) {
        if (thinking.isNotBlank()) ThinkingBlock(thinking)
        if (text.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AphroditeSpinner(size = 12.dp)
                Box(Modifier.size(AphroditeSpacing.sm))
                Text(
                    text = "Thinking…",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            MarkdownContent(text = text, renderer = renderer)
        }
    }
}

@Composable
private fun ThinkingBlock(text: String) {
    val colors = aphroditeColors
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(colors.toggleBg)
            .padding(AphroditeSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.androidClickable({ expanded = !expanded }, Role.Button),
        ) {
            Icon(
                imageVector = if (expanded) AphroditeIcons.ChevronDown else AphroditeIcons.ChevronRight,
                contentDescription = null,
                tint = colors.colorMuted,
                modifier = Modifier.size(12.dp),
            )
            Box(Modifier.size(AphroditeSpacing.sm))
            Text(
                text = "Reasoning",
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        if (expanded) {
            Box(Modifier.size(AphroditeSpacing.sm))
            Text(
                text = text,
                color = colors.colorMuted,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = androidx.compose.material3.MaterialTheme.typography.bodySmall.fontSize,
                ),
            )
        }
    }
    Box(Modifier.size(AphroditeSpacing.sm))
}

/** Renders parsed markdown, giving code blocks and tables their own treatment. */
@Composable
private fun MarkdownContent(text: String, renderer: MarkdownRenderer) {
    val colors = aphroditeColors
    val blocks = remember(text, renderer) { renderer.parse(text) }

    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        color = colors.fg,
                        style = when (block.level) {
                            1 -> androidx.compose.material3.MaterialTheme.typography.headlineSmall
                            2 -> androidx.compose.material3.MaterialTheme.typography.titleLarge
                            3 -> androidx.compose.material3.MaterialTheme.typography.titleMedium
                            else -> androidx.compose.material3.MaterialTheme.typography.titleSmall
                        },
                        modifier = Modifier.padding(top = AphroditeSpacing.sm, bottom = AphroditeSpacing.xs),
                    )
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = AphroditeSpacing.sm),
                )

                is MarkdownBlock.CodeBlock -> CodeBlock(block)

                is MarkdownBlock.BulletList -> block.items.forEach { item ->
                    Row(modifier = Modifier.padding(bottom = AphroditeSpacing.xs)) {
                        Text(
                            text = if (item.depth == 0) "•" else "◦",
                            color = colors.colorMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = (item.depth * 12).dp, end = AphroditeSpacing.sm),
                        )
                        Text(
                            text = item.text,
                            color = colors.fg,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is MarkdownBlock.OrderedList -> block.items.forEachIndexed { index, item ->
                    Row(modifier = Modifier.padding(bottom = AphroditeSpacing.xs)) {
                        Text(
                            text = "${block.start + index}.",
                            color = colors.colorMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = AphroditeSpacing.sm),
                        )
                        Text(
                            text = item.text,
                            color = colors.fg,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is MarkdownBlock.Quote -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AphroditeSpacing.xs)
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(colors.toggleBg)
                        .padding(AphroditeSpacing.sm),
                ) {
                    block.blocks.forEach { inner ->
                        if (inner is MarkdownBlock.Paragraph) {
                            Text(
                                text = inner.text,
                                color = colors.colorMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                is MarkdownBlock.Table -> MarkdownTable(block)

                MarkdownBlock.Rule -> AphroditeDivider(
                    modifier = Modifier.padding(vertical = AphroditeSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.CodeBlock) {
    val colors = aphroditeColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AphroditeSpacing.sm)
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(colors.codeBg)
            .border(1.dp, colors.border, RoundedCornerShape(AphroditeRadius.xs)),
    ) {
        val language = block.language
        if (language != null) {
            Text(
                text = language,
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.xs),
            )
            AphroditeDivider()
        }
        Text(
            text = block.code,
            color = colors.codeFg,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = androidx.compose.material3.MaterialTheme.typography.bodySmall.fontSize,
                lineHeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.lineHeight,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(AphroditeSpacing.sm)
                .verticalScroll(rememberScrollState(), enabled = false)
                .heightIn(max = 420.dp),
        )
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val colors = aphroditeColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AphroditeSpacing.sm)
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .border(1.dp, colors.border, RoundedCornerShape(AphroditeRadius.xs)),
    ) {
        Row(modifier = Modifier.background(colors.toggleBg)) {
            table.header.forEach { cell ->
                Text(
                    text = cell,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(AphroditeSpacing.sm),
                )
            }
        }
        table.rows.forEach { row ->
            AphroditeDivider()
            Row {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        color = colors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(AphroditeSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    enabled: Boolean,
) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel)
            .padding(AphroditeSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(AphroditeRadius.sm))
                .background(colors.inputBg)
                .border(1.dp, colors.inputBorder, RoundedCornerShape(AphroditeRadius.sm))
                .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.md),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = if (enabled) "Send a message" else "Select a chat first",
                    color = colors.colorMuted.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = TextStyle(
                    color = colors.fg,
                    fontSize = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(colors.accentPrimary),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        AphroditeIconButton(
            icon = if (isStreaming) AphroditeIcons.Stop else AphroditeIcons.Send,
            contentDescription = if (isStreaming) "Stop" else "Send",
            onClick = { if (isStreaming) onStop() else onSend() },
            enabled = enabled && (isStreaming || value.isNotBlank()),
            tint = if (isStreaming) colors.colorError else colors.accentPrimary,
            filled = true,
            size = 44.dp,
        )
    }
}

/** Small helper so the clickable modifier stays readable at the call sites. */
private fun Modifier.androidClickable(
    onClick: () -> Unit,
    role: Role? = null,
): Modifier = clickable(role = role, onClick = onClick)
