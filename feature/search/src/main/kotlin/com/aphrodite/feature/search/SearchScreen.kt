package com.aphrodite.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.database.dao.SearchHit
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * One search box over everything the user has written.
 *
 * Each result is labelled with what it is. Without that, a note and a document look identical in a
 * mixed list, and the user cannot tell where a match came from.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
        ) {
            AphroditeTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Search chats, notes, documents, memories, contacts",
                leadingIcon = AphroditeIcons.Search,
                modifier = Modifier.weight(1f),
            )
            if (state.query.isNotEmpty()) {
                AphroditeIconButton(
                    icon = AphroditeIcons.Close,
                    contentDescription = "Clear",
                    onClick = onClear,
                    size = 28.dp,
                )
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = colors.colorError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = AphroditeSpacing.md),
            )
        }

        when {
            state.searching && state.results.isEmpty() -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.xl),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AphroditeSpinner()
                }
            }

            !state.hasSearched -> {
                AphroditeEmptyState(
                    title = "Search everything",
                    description = "Chats, notes, documents, memories and contacts in one list.",
                    icon = AphroditeIcons.Search,
                )
            }

            state.results.isEmpty() -> {
                AphroditeEmptyState(
                    title = "No matches",
                    description = "Nothing contains \"${state.query}\".",
                    icon = AphroditeIcons.Search,
                )
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.kind + it.id }) { hit ->
                        SearchRow(hit)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.xs)) {
        AphroditeCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = hit.kind.uppercase(),
                color = colors.colorAccent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = hit.title.ifBlank { "(untitled)" },
                color = colors.fg,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            hit.snippet
                ?.takeIf { it.isNotBlank() && it != hit.title }
                ?.let { snippet ->
                    Text(
                        text = snippet,
                        color = colors.colorSubheader,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}
