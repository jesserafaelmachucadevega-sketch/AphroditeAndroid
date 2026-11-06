package com.aphrodite.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.CalendarEventEntity
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
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Calendar.
 *
 * A month grid over the selected day's agenda, splitting into two panes on wide layouts. Local
 * events only for now: CalDAV accounts are modelled but not synced.
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: (EventDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AphroditeTwoPane(
        showDetail = true,
        modifier = modifier,
        listWidth = 420.dp,
        list = {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MonthHeader(
                    month = state.month,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onAdd = onAdd,
                )
                MonthGrid(
                    month = state.month,
                    selected = state.selectedDay,
                    marked = state.daysWithEvents,
                    onSelect = onSelectDay,
                )
            }
        },
        detail = {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (state.error != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(aphroditeColors.colorError.copy(alpha = 0.14f))
                            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error,
                            color = aphroditeColors.colorError,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        AphroditeIconButton(
                            icon = AphroditeIcons.Close,
                            contentDescription = "Dismiss",
                            onClick = onDismissError,
                            size = 22.dp,
                            tint = aphroditeColors.colorError,
                        )
                    }
                }
                if (state.draft != null) {
                    EventEditor(
                        draft = state.draft,
                        onChange = onDraftChange,
                        onCancel = onCancelEdit,
                        onSave = onSave,
                    )
                } else {
                    DayAgenda(
                        day = state.selectedDay,
                        events = state.dayEvents,
                        onAdd = onAdd,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        },
    )
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AphroditeIconButton(
            icon = AphroditeIcons.ChevronLeft,
            contentDescription = "Previous month",
            onClick = onPrevious,
            size = 28.dp,
        )
        Text(
            text = month.format(MONTH_FORMAT),
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AphroditeSpacing.sm),
        )
        AphroditeIconButton(
            icon = AphroditeIcons.ChevronRight,
            contentDescription = "Next month",
            onClick = onNext,
            size = 28.dp,
        )
        AphroditeIconButton(
            icon = AphroditeIcons.Plus,
            contentDescription = "New event",
            onClick = onAdd,
            size = 28.dp,
            tint = colors.accentPrimary,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    marked: Set<Int>,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = aphroditeColors
    // Monday-first weeks, matching the locale most of Europe and the upstream app use.
    val firstOfMonth = month.atDay(1)
    val leading = (firstOfMonth.dayOfWeek.value - 1).coerceAtLeast(0)
    val daysInMonth = month.lengthOfMonth()
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AphroditeSpacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Box(Modifier.size(AphroditeSpacing.xs))

        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayNumber = cellIndex - leading + 1
                    val inMonth = dayNumber in 1..daysInMonth
                    val date = if (inMonth) month.atDay(dayNumber) else null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.15f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(AphroditeRadius.xs))
                            .background(
                                when {
                                    date == null -> androidx.compose.ui.graphics.Color.Transparent
                                    date == selected -> colors.accentPrimary.copy(alpha = 0.22f)
                                    date == LocalDate.now() -> colors.toggleBg
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .then(
                                if (date != null) {
                                    Modifier.clickable(role = Role.Button) { onSelect(date) }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    color = if (date == selected) colors.accentPrimary else colors.fg,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    fontWeight = if (date == selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (date.dayOfMonth in marked && YearMonth.from(date) == month) {
                                    Box(
                                        Modifier
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(AphroditeRadius.pill))
                                            .background(
                                                if (date == selected) colors.accentPrimary else colors.colorAccent,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(
    day: LocalDate,
    events: List<CalendarEventEntity>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = aphroditeColors
    val zone = ZoneId.systemDefault()

    Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.lg)) {
        Text(
            text = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        )
        Box(Modifier.size(AphroditeSpacing.md))

        if (events.isEmpty()) {
            AphroditeEmptyState(
                title = "Nothing scheduled",
                description = "Add an event for this day.",
                icon = AphroditeIcons.Calendar,
                action = { AphroditeButton(text = "New event", onClick = onAdd, icon = AphroditeIcons.Plus) },
            )
        } else {
            events.forEach { event ->
                val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startAt), zone)
                val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endAt), zone)
                AphroditeCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = AphroditeSpacing.sm),
                    onClick = { onEdit(event.id) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(AphroditeRadius.pill))
                                .background(colors.accentPrimary),
                        )
                        Box(Modifier.size(AphroditeSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.summary,
                                color = colors.fg,
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (event.isAllDay) {
                                    "All day"
                                } else {
                                    "${start.format(TIME_FORMAT)} – ${end.format(TIME_FORMAT)}"
                                },
                                color = colors.colorMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                        AphroditeIconButton(
                            icon = AphroditeIcons.Trash,
                            contentDescription = "Delete",
                            onClick = { onDelete(event.id) },
                            size = 26.dp,
                            tint = colors.colorError,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventEditor(
    draft: EventDraft,
    onChange: (EventDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = aphroditeColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
    ) {
        AphroditeSectionHeader(
            title = if (draft.isNew) "New event" else "Edit event",
            icon = AphroditeIcons.Calendar,
        )
        Text(
            text = draft.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")),
            color = colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        AphroditeTextField(
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            placeholder = "Title",
            leadingIcon = AphroditeIcons.Type,
        )
        AphroditeTextField(
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            placeholder = "Notes (optional)",
            leadingIcon = AphroditeIcons.Notes,
        )
        AphroditeSwitchRow(
            label = "All day",
            checked = draft.allDay,
            onCheckedChange = { onChange(draft.copy(allDay = it)) },
        )

        if (!draft.allDay) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                (7..21).forEach { hour ->
                    val selected = draft.startHour == hour
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AphroditeRadius.xs))
                            .background(if (selected) colors.accentPrimary.copy(alpha = 0.2f) else colors.toggleBg)
                            .border(
                                1.dp,
                                if (selected) colors.accentPrimary else colors.border,
                                RoundedCornerShape(AphroditeRadius.xs),
                            )
                            .clickable(role = Role.Button) {
                                onChange(draft.copy(startHour = hour, startMinute = 0))
                            }
                            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                    ) {
                        Text(
                            text = "%02d:00".format(hour),
                            color = if (selected) colors.accentPrimary else colors.fg,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Text(
                text = "Duration",
                color = colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                listOf(30, 60, 90, 120, 240).forEach { minutes ->
                    val selected = draft.durationMinutes == minutes
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AphroditeRadius.xs))
                            .background(if (selected) colors.accentPrimary.copy(alpha = 0.2f) else colors.toggleBg)
                            .border(
                                1.dp,
                                if (selected) colors.accentPrimary else colors.border,
                                RoundedCornerShape(AphroditeRadius.xs),
                            )
                            .clickable(role = Role.Button) {
                                onChange(draft.copy(durationMinutes = minutes))
                            }
                            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                    ) {
                        Text(
                            text = if (minutes < 60) "${minutes}m" else "${minutes / 60}h",
                            color = if (selected) colors.accentPrimary else colors.fg,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = if (draft.isNew) "Add" else "Save",
                onClick = onSave,
                icon = AphroditeIcons.Check,
                enabled = draft.title.isNotBlank(),
            )
            AphroditeButton(
                text = "Cancel",
                onClick = onCancel,
                style = AphroditeButtonStyle.Secondary,
            )
        }
        Box(Modifier.size(AphroditeSpacing.huge))
    }
}
