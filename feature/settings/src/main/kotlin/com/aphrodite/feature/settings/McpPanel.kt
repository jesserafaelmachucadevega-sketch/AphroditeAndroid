package com.aphrodite.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.database.entity.McpServerEntity
import com.aphrodite.core.database.entity.McpToolEntity
import com.aphrodite.core.database.entity.McpTransport
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeSwitchRow
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** The server form's contents while it is open. */
data class McpDraft(
    val name: String = "",
    val transport: McpTransport = McpTransport.StreamableHttp,
    val url: String = "",
    val token: String = "",
)

data class McpState(
    val servers: List<McpServerEntity> = emptyList(),
    val tools: List<McpToolEntity> = emptyList(),
    val status: Map<String, String> = emptyMap(),
    val draft: McpDraft? = null,
    val expanded: Set<String> = emptySet(),
)

data class McpHandlers(
    val onAdd: () -> Unit,
    val onChange: (McpDraft) -> Unit,
    val onCancel: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: (String) -> Unit,
    val onToggle: (String, Boolean) -> Unit,
    val onRefresh: (String) -> Unit,
    val onToggleExpanded: (String) -> Unit,
)

/**
 * MCP servers.
 *
 * Android has no equivalent of Odysseus's `stdio` transport — it cannot spawn a subprocess — so
 * only the two network transports are offered, and the panel says so rather than leaving it a
 * mystery why a command-based server cannot be added.
 */
@Composable
fun McpSettingsPanel(
    state: McpState,
    handlers: McpHandlers,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AphroditeSpacing.lg,
                end = AphroditeSpacing.lg,
                top = AphroditeSpacing.lg,
                bottom = AphroditeSpacing.huge,
            ),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.lg),
    ) {
        if (state.draft != null) {
            McpServerForm(draft = state.draft, handlers = handlers)
            AphroditeDivider()
        }

        Column {
            AphroditeSectionHeader(title = "MCP servers", icon = AphroditeIcons.Server)
            Text(
                text = "Tools from these servers become available to the agent automatically. " +
                    "Run the server as a service: Android cannot spawn a local process, so the " +
                    "stdio transport is not available.",
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Box(Modifier.size(AphroditeSpacing.md))

            if (state.servers.isEmpty()) {
                AphroditeEmptyState(
                    title = "No servers yet",
                    description = "Add one to give the model more tools.",
                    icon = AphroditeIcons.Server,
                    action = {
                        AphroditeButton(text = "Add server", onClick = handlers.onAdd, icon = AphroditeIcons.Plus)
                    },
                )
            } else {
                state.servers.forEach { server ->
                    McpServerCard(
                        server = server,
                        status = state.status[server.id],
                        tools = state.tools.filter { it.serverId == server.id },
                        expanded = server.id in state.expanded,
                        onToggleExpanded = { handlers.onToggleExpanded(server.id) },
                        onToggle = { handlers.onToggle(server.id, it) },
                        onRefresh = { handlers.onRefresh(server.id) },
                        onDelete = { handlers.onDelete(server.id) },
                    )
                    Box(Modifier.size(AphroditeSpacing.sm))
                }
                AphroditeButton(
                    text = "Add server",
                    onClick = handlers.onAdd,
                    icon = AphroditeIcons.Plus,
                    style = AphroditeButtonStyle.Secondary,
                )
            }
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServerEntity,
    status: String?,
    tools: List<McpToolEntity>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = aphroditeColors
    AphroditeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${server.transport.label} · ${server.url}",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AphroditeSwitchRow(
                label = "",
                checked = server.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.fillMaxWidth(0.3f),
            )
        }

        val line = status ?: server.lastError ?: "Not connected yet"
        val ok = status?.startsWith("Connected") == true || server.lastError == null && server.toolCount > 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ok) AphroditeIcons.CheckCircle else AphroditeIcons.AlertCircle,
                contentDescription = null,
                tint = if (ok) colors.colorSuccess else colors.colorMuted,
                modifier = Modifier.size(10.dp),
            )
            Box(Modifier.size(AphroditeSpacing.xs))
            Text(
                text = line,
                color = if (ok) colors.colorSuccess else colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(Modifier.size(AphroditeSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AphroditeButton(
                text = if (expanded) "Hide tools" else "Show ${tools.size} tools",
                onClick = onToggleExpanded,
                style = AphroditeButtonStyle.Ghost,
                size = AphroditeButtonSize.Small,
                enabled = tools.isNotEmpty(),
            )
            Box(Modifier.weight(1f))
            AphroditeIconButton(
                icon = AphroditeIcons.Refresh,
                contentDescription = "Reconnect",
                onClick = onRefresh,
                size = 26.dp,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Trash,
                contentDescription = "Delete",
                onClick = onDelete,
                size = 26.dp,
                tint = colors.colorError,
            )
        }

        if (expanded) {
            Box(Modifier.size(AphroditeSpacing.sm))
            AphroditeDivider()
            tools.forEach { tool ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = AphroditeSpacing.xs)) {
                    Text(
                        text = tool.name,
                        color = colors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                    tool.description?.let {
                        Text(
                            text = it,
                            color = colors.colorMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerForm(draft: McpDraft, handlers: McpHandlers) {
    val colors = aphroditeColors
    Column(verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md)) {
        AphroditeSectionHeader(title = "New server", icon = AphroditeIcons.Server)
        AphroditeTextField(
            value = draft.name,
            onValueChange = { handlers.onChange(draft.copy(name = it)) },
            placeholder = "Name, e.g. Files",
            leadingIcon = AphroditeIcons.Type,
        )
        AphroditeTextField(
            value = draft.url,
            onValueChange = { handlers.onChange(draft.copy(url = it)) },
            placeholder = "https://host:3000/mcp",
            leadingIcon = AphroditeIcons.Link,
        )
        AphroditeTextField(
            value = draft.token,
            onValueChange = { handlers.onChange(draft.copy(token = it)) },
            placeholder = "Bearer token (optional, stored encrypted)",
            leadingIcon = AphroditeIcons.Key,
        )

        Text(
            text = "Transport",
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
        ) {
            McpTransport.entries.forEach { transport ->
                val selected = draft.transport == transport
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(if (selected) colors.accentPrimary.copy(alpha = 0.2f) else colors.toggleBg)
                        .border(
                            1.dp,
                            if (selected) colors.accentPrimary else colors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.RadioButton) {
                            handlers.onChange(draft.copy(transport = transport))
                        }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = transport.label,
                        color = if (selected) colors.accentPrimary else colors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = "Add and connect",
                onClick = handlers.onSave,
                icon = AphroditeIcons.Check,
                enabled = draft.name.isNotBlank() && draft.url.isNotBlank(),
            )
            AphroditeButton(
                text = "Cancel",
                onClick = handlers.onCancel,
                style = AphroditeButtonStyle.Secondary,
            )
        }
    }
}
