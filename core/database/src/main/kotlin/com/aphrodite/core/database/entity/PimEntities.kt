package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** How a note is presented in the grid. */
enum class NoteKind { Text, Checklist, Draft, Goal }

/**
 * A note.
 *
 * The upstream app renders notes as cards that can be plain text, a checklist, a draft or a
 * goal. Checklist content lives in [NoteItemEntity] rows so items can be toggled and reordered
 * independently.
 */
@Entity(
    tableName = "notes",
    indices = [Index("pinned"), Index("archived"), Index("kind"), Index("updated_at"), Index("reminder_at")],
)
@Serializable
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val body: String = "",
    val kind: NoteKind = NoteKind.Text,
    val color: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "reminder_at") val reminderAt: Long? = null,
    @ColumnInfo(name = "recurrence_rule") val recurrenceRule: String? = null,
    /** For goals: the target count and how many are done. */
    @ColumnInfo(name = "goal_target") val goalTarget: Int? = null,
    @ColumnInfo(name = "goal_progress") val goalProgress: Int = 0,
    @ColumnInfo(name = "tags") val tags: String? = null,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** One checklist entry inside a note. */
@Entity(
    tableName = "note_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("note_id"), Index("note_id", "sort_order")],
)
@Serializable
data class NoteItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "note_id") val noteId: String,
    val text: String,
    val checked: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** What starts a scheduled task. */
enum class TaskTrigger { Manual, Interval, Daily, Weekly, Cron, OnEvent, Webhook }

/** What a task does when it runs. */
enum class TaskAction { AgentPrompt, BuiltinAction, Webhook }

/** A scheduled or manually triggered task. */
@Entity(
    tableName = "tasks",
    indices = [Index("enabled"), Index("next_run_at"), Index("trigger_kind")],
)
@Serializable
data class TaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "trigger_kind") val triggerKind: TaskTrigger = TaskTrigger.Manual,
    /** Interval in minutes, or a cron expression, depending on the trigger. */
    @ColumnInfo(name = "trigger_value") val triggerValue: String? = null,
    @ColumnInfo(name = "action_kind") val actionKind: TaskAction = TaskAction.AgentPrompt,
    /** The prompt for agent tasks, or the builtin action id. */
    val payload: String,
    @ColumnInfo(name = "output_target") val outputTarget: String? = null,
    @ColumnInfo(name = "session_id") val sessionId: String? = null,
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "endpoint_id") val endpointId: String? = null,
    val enabled: Boolean = true,
    /** Notifications: whether to alert on success, failure, or both. */
    @ColumnInfo(name = "notify_on") val notifyOn: String = "failure",
    @ColumnInfo(name = "webhook_token") val webhookToken: String? = null,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long? = null,
    @ColumnInfo(name = "next_run_at") val nextRunAt: Long? = null,
    @ColumnInfo(name = "run_count") val runCount: Int = 0,
    @ColumnInfo(name = "failure_count") val failureCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The record of one execution of a task. */
@Entity(
    tableName = "task_runs",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("task_id"), Index("started_at"), Index("status")],
)
@Serializable
data class TaskRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val status: String,
    val output: String? = null,
    val error: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
)

/** A CalDAV account used to sync calendars. */
@Entity(tableName = "calendar_accounts", indices = [Index("enabled")])
@Serializable
data class CalendarAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "server_url") val serverUrl: String,
    val username: String,
    /** Key into the encrypted secret store. */
    @ColumnInfo(name = "password_ref") val passwordRef: String? = null,
    @ColumnInfo(name = "sync_direction") val syncDirection: String = "both",
    @ColumnInfo(name = "sync_window_days") val syncWindowDays: Int = 90,
    val enabled: Boolean = true,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A calendar, either local or mirrored from CalDAV. */
@Entity(
    tableName = "calendars",
    foreignKeys = [
        ForeignKey(
            entity = CalendarAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("account_id"), Index("visible")],
)
@Serializable
data class CalendarEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String? = null,
    val name: String,
    val color: String? = null,
    @ColumnInfo(name = "remote_href") val remoteHref: String? = null,
    @ColumnInfo(name = "read_only") val readOnly: Boolean = false,
    /** Stable identity for lazily created local defaults. */
    @ColumnInfo(name = "local_key") val localKey: String? = null,
    val visible: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

/**
 * A calendar event.
 *
 * Recurrence is stored as an RRULE string and expanded on read, matching the server-side
 * behaviour the upstream app relies on. [remoteHref] and [remoteEtag] track the CalDAV object
 * so writeback can detect remote changes, and [syncPending] marks local edits not yet pushed.
 */
@Entity(
    tableName = "calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendar_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("calendar_id"), Index("start_at"), Index("uid"), Index("sync_pending")],
)
@Serializable
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "calendar_id") val calendarId: String,
    val uid: String,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    @ColumnInfo(name = "is_all_day") val isAllDay: Boolean = false,
    @ColumnInfo(name = "is_utc") val isUtc: Boolean = false,
    val timezone: String? = null,
    @ColumnInfo(name = "recurrence_rule") val recurrenceRule: String? = null,
    /** Comma separated exception dates for recurring events. */
    @ColumnInfo(name = "recurrence_exceptions") val recurrenceExceptions: String? = null,
    val status: String? = null,
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int? = null,
    @ColumnInfo(name = "remote_href") val remoteHref: String? = null,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    @ColumnInfo(name = "sync_pending") val syncPending: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A contact, either local or mirrored from CardDAV. */
@Entity(
    tableName = "contacts",
    indices = [Index("display_name"), Index("email"), Index(value = ["uid"], unique = true)],
)
@Serializable
data class ContactEntity(
    @PrimaryKey val id: String,
    val uid: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val email: String? = null,
    val phone: String? = null,
    val organization: String? = null,
    val title: String? = null,
    val notes: String? = null,
    /** Additional emails and phones as a JSON array, for contacts with several. */
    @ColumnInfo(name = "emails_json") val emailsJson: String? = null,
    @ColumnInfo(name = "phones_json") val phonesJson: String? = null,
    @ColumnInfo(name = "remote_href") val remoteHref: String? = null,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    @ColumnInfo(name = "sync_pending") val syncPending: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
