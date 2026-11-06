package com.aphrodite.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * The device's images and the app's own uploads in one grid.
 *
 * The permission prompt is a button rather than something shown on entry: a gallery that demands
 * storage access before displaying anything is asking for trust it has not earned yet, and the
 * app's own uploads are visible without it.
 */
@Composable
fun GalleryScreen(
    state: GalleryUiState,
    onQueryChange: (String) -> Unit,
    onOpen: (GalleryItem) -> Unit,
    onClose: () -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onPermissionRequest: () -> Unit,
    onEdit: (GalleryItem) -> Unit,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    state.editing?.let { item ->
        ImageEditorRoute(item = item, onClose = onCloseEditor)
        return
    }

    if (state.openIndex != null) {
        Viewer(
            state = state,
            onClose = onClose,
            onDelete = onDelete,
            onEdit = onEdit,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
        ) {
            AphroditeTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Filter by name",
                leadingIcon = AphroditeIcons.Search,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Refresh,
                contentDescription = "Refresh",
                onClick = onRefresh,
                size = 28.dp,
            )
        }

        if (!state.permissionGranted) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AphroditeSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                Text(
                    text = "Device photos are hidden without storage access. Uploaded images are shown.",
                    color = colors.colorSubheader,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                AphroditeButton(
                    text = "Allow",
                    onClick = onPermissionRequest,
                    style = AphroditeButtonStyle.Secondary,
                    size = AphroditeButtonSize.Small,
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
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AphroditeSpinner()
            }

            state.visible.isEmpty() -> AphroditeEmptyState(
                title = if (state.query.isBlank()) "No images" else "No matches",
                description = if (state.query.isBlank()) {
                    "Photos on this device and images uploaded to Aphrodite appear here."
                } else {
                    "No image name contains \"${state.query}\"."
                },
                icon = AphroditeIcons.Gallery,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize().padding(AphroditeSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
            ) {
                items(state.visible, key = { it.source.name + it.id }) { item ->
                    Thumbnail(item = item, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(item: GalleryItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(aphroditeColors.bg)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.source == GallerySource.Upload) {
            // Marked so it is obvious which images the app can actually delete.
            Text(
                text = "app",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * Full-screen viewer with paging and pinch zoom.
 *
 * Zoom resets between pages: carrying a zoom level into the next image leaves you looking at a
 * corner of it, which reads as the viewer being broken.
 */
@Composable
private fun Viewer(
    state: GalleryUiState,
    onClose: () -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onEdit: (GalleryItem) -> Unit,
) {
    val items = state.visible
    if (items.isEmpty()) return

    val start = (state.openIndex ?: 0).coerceIn(0, items.size - 1)
    val pagerState = rememberPagerState(initialPage = start) { items.size }
    val current = items.getOrNull(pagerState.currentPage) ?: items[start]
    val colors = aphroditeColors

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            var scale by remember(pagerState.currentPage) { mutableFloatStateOf(1f) }
            val transform = rememberTransformableState { zoomChange, _, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 6f)
            }
            Box(
                modifier = Modifier.fillMaxSize().transformable(transform),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = items[page].uri,
                    contentDescription = items[page].name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AphroditeIconButton(
                icon = AphroditeIcons.Close,
                contentDescription = "Close",
                onClick = onClose,
                tint = Color.White,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current.name.ifBlank { "Image" },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${pagerState.currentPage + 1} of ${items.size}" +
                        if (current.source == GallerySource.Upload) " · stored in Aphrodite" else "",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            AphroditeIconButton(
                icon = AphroditeIcons.Edit,
                contentDescription = "Edit",
                onClick = { onEdit(current) },
                tint = Color.White,
            )
            if (current.source == GallerySource.Upload) {
                AphroditeIconButton(
                    icon = AphroditeIcons.Trash,
                    contentDescription = "Delete",
                    onClick = { onDelete(current) },
                    tint = colors.colorError,
                )
            }
        }
    }
}
