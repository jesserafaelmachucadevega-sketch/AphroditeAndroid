package com.aphrodite.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.CalendarDao
import com.aphrodite.core.database.entity.CalendarEntity
import com.aphrodite.core.database.entity.CalendarEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** An event being edited. */
data class EventDraft(
    val id: String? = null,
    val title: String = "",
    val day: LocalDate,
    val allDay: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val durationMinutes: Int = 60,
    val description: String = "",
) {
    val isNew: Boolean get() = id == null
}

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val events: List<CalendarEventEntity> = emptyList(),
    val calendars: List<CalendarEntity> = emptyList(),
    val draft: EventDraft? = null,
    val error: String? = null,
) {
    /** Events falling on the selected day, sorted by start. */
    val dayEvents: List<CalendarEventEntity>
        get() = events.filter { it.occursOn(selectedDay) }.sortedBy { it.startAt }

    /** Days in the visible month that have at least one event. */
    val daysWithEvents: Set<Int>
        get() = events.filter { it.occursOn(it.localStart().toLocalDate()) }
            .mapNotNull { event ->
                val start = event.localStart().toLocalDate()
                if (YearMonth.from(start) == month) start.dayOfMonth else null
            }
            .toSet()
}

private fun CalendarEventEntity.localStart(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), ZoneId.systemDefault())

private fun CalendarEventEntity.occursOn(day: LocalDate): Boolean {
    val zone = ZoneId.systemDefault()
    val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), zone)
    val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endAt), zone)
    return !day.atStartOfDay().isAfter(end) && !day.plusDays(1).atStartOfDay().isBefore(start)
}

/**
 * Calendar.
 *
 * Recurrence is stored as an RRULE but not yet expanded here: a repeating event appears once, on
 * its start date. Expanding occurrences is the next step, and pretending otherwise would show a
 * weekly meeting on only one week.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val dao: CalendarDao,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private var defaultCalendarId: String? = null

    init {
        viewModelScope.launch {
            dao.observeCalendars().collect { calendars ->
                _state.update { it.copy(calendars = calendars) }
            }
        }
        viewModelScope.launch {
            ensureDefaultCalendar()
            refreshEvents()
        }
    }

    /** Creates a local calendar if the user has none, so events have somewhere to live. */
    private suspend fun ensureDefaultCalendar() {
        val existing = dao.calendarByLocalKey(DEFAULT_KEY)
        val id = existing?.id ?: run {
            val id = UUID.randomUUID().toString()
            dao.upsertCalendar(
                CalendarEntity(
                    id = id,
                    accountId = null,
                    name = "Personal",
                    color = "#E06C75",
                    localKey = DEFAULT_KEY,
                    visible = true,
                ),
            )
            id
        }
        defaultCalendarId = id
    }

    private suspend fun refreshEvents() {
        val month = _state.value.month
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.observeEventsInRange(start, end).collect { events ->
            _state.update { it.copy(events = events) }
        }
    }

    fun previousMonth() = changeMonth(-1)

    fun nextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        val month = _state.value.month.plusMonths(delta)
        _state.update { it.copy(month = month) }
        viewModelScope.launch { refreshEvents() }
    }

    fun selectDay(day: LocalDate) = _state.update {
        it.copy(
            selectedDay = day,
            // Following the selected day with the view keeps the grid and the list in step.
            month = YearMonth.from(day),
        )
    }

    fun startAdd() = _state.update {
        it.copy(draft = EventDraft(day = it.selectedDay), error = null)
    }

    fun startEdit(eventId: String) {
        viewModelScope.launch {
            val event = dao.event(eventId) ?: return@launch
            val zone = ZoneId.systemDefault()
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startAt), zone)
            _state.update {
                it.copy(
                    draft = EventDraft(
                        id = event.id,
                        title = event.summary,
                        day = start.toLocalDate(),
                        allDay = event.isAllDay,
                        startHour = start.hour,
                        startMinute = start.minute,
                        durationMinutes = ((event.endAt - event.startAt) / 60_000L).toInt()
                            .coerceAtLeast(15),
                        description = event.description.orEmpty(),
                    ),
                    error = null,
                )
            }
        }
    }

    fun onDraftChange(draft: EventDraft) = _state.update { it.copy(draft = draft) }

    fun cancelEdit() = _state.update { it.copy(draft = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (draft.title.isBlank()) {
            _state.update { it.copy(error = "Give the event a title.") }
            return
        }
        val calendarId = defaultCalendarId ?: run {
            _state.update { it.copy(error = "No calendar available.") }
            return
        }
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val start = if (draft.allDay) {
                draft.day.atStartOfDay(zone)
            } else {
                LocalDateTime.of(draft.day, LocalTime.of(draft.startHour, draft.startMinute))
                    .atZone(zone)
            }
            val duration = if (draft.allDay) 24 * 60L else draft.durationMinutes.toLong()
            val end = start.plusMinutes(duration)

            val existing = draft.id?.let { dao.event(it) }
            dao.upsertEvent(
                CalendarEventEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    calendarId = existing?.calendarId ?: calendarId,
                    uid = existing?.uid ?: UUID.randomUUID().toString(),
                    summary = draft.title.trim(),
                    description = draft.description.takeIf { it.isNotBlank() },
                    location = existing?.location,
                    startAt = start.toInstant().toEpochMilli(),
                    endAt = end.toInstant().toEpochMilli(),
                    isAllDay = draft.allDay,
                    isUtc = false,
                    timezone = zone.id,
                    recurrenceRule = existing?.recurrenceRule,
                    recurrenceExceptions = existing?.recurrenceExceptions,
                    status = existing?.status,
                    reminderMinutes = existing?.reminderMinutes,
                    remoteHref = existing?.remoteHref,
                    remoteEtag = existing?.remoteEtag,
                    // A local edit needs pushing if this calendar ever syncs.
                    syncPending = existing?.remoteHref != null,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            refreshEvents()
            _state.update { it.copy(draft = null) }
        }
    }

    fun delete(eventId: String) {
        viewModelScope.launch {
            dao.deleteEvent(eventId)
            refreshEvents()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val DEFAULT_KEY = "local:personal"
    }
}
