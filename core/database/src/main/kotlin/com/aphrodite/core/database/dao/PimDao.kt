package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aphrodite.core.database.entity.CalendarAccountEntity
import com.aphrodite.core.database.entity.CalendarEntity
import com.aphrodite.core.database.entity.CalendarEventEntity
import com.aphrodite.core.database.entity.ContactEntity
import com.aphrodite.core.database.entity.NoteEntity
import com.aphrodite.core.database.entity.NoteItemEntity
import com.aphrodite.core.database.entity.TaskEntity
import com.aphrodite.core.database.entity.TaskRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY pinned DESC, sort_order ASC, updated_at DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archived = 1 ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE reminder_at IS NOT NULL AND reminder_at > :now ORDER BY reminder_at ASC")
    suspend fun pendingReminders(now: Long): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE notes SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET archived = :archived, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE notes SET reminder_at = :at, updated_at = :now WHERE id = :id")
    suspend fun setReminder(id: String, at: Long?, now: Long)

    @Query("UPDATE notes SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int, now: Long)

    @Query("SELECT * FROM note_items WHERE note_id = :noteId ORDER BY sort_order ASC")
    fun observeItems(noteId: String): Flow<List<NoteItemEntity>>

    @Query("SELECT * FROM note_items WHERE note_id = :noteId ORDER BY sort_order ASC")
    suspend fun items(noteId: String): List<NoteItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<NoteItemEntity>)

    @Query("DELETE FROM note_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE note_items SET checked = :checked WHERE id = :id")
    suspend fun setItemChecked(id: String, checked: Boolean)

    /** Writes a note and replaces its checklist in one transaction. */
    @Transaction
    suspend fun saveWithItems(note: NoteEntity, items: List<NoteItemEntity>) {
        upsert(note)
        if (items.isNotEmpty()) upsertItems(items)
    }
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY enabled DESC, name ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE enabled = 1 AND trigger_kind != 'Manual' ORDER BY next_run_at ASC")
    suspend fun scheduled(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE enabled = 1 AND next_run_at IS NOT NULL AND next_run_at <= :now")
    suspend fun due(now: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tasks SET enabled = :enabled, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE tasks SET next_run_at = :nextRun, updated_at = :now WHERE id = :id")
    suspend fun setNextRun(id: String, nextRun: Long?, now: Long)

    @Query("UPDATE tasks SET next_run_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun clearSchedule(id: String, now: Long)

    @Query("SELECT * FROM task_runs WHERE task_id = :taskId ORDER BY started_at DESC LIMIT :limit")
    fun observeRuns(taskId: String, limit: Int): Flow<List<TaskRunEntity>>

    @Query("SELECT * FROM task_runs ORDER BY started_at DESC LIMIT :limit")
    fun observeRecentRuns(limit: Int): Flow<List<TaskRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: TaskRunEntity)

    @Update
    suspend fun updateRun(run: TaskRunEntity)

    @Query("DELETE FROM task_runs WHERE task_id = :taskId")
    suspend fun deleteRuns(taskId: String)
}

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar_accounts ORDER BY name ASC")
    fun observeAccounts(): Flow<List<CalendarAccountEntity>>

    @Query("SELECT * FROM calendar_accounts WHERE id = :id")
    suspend fun account(id: String): CalendarAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: CalendarAccountEntity)

    @Query("DELETE FROM calendar_accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("SELECT * FROM calendars ORDER BY sort_order ASC, name ASC")
    fun observeCalendars(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun calendar(id: String): CalendarEntity?

    @Query("SELECT * FROM calendars WHERE local_key = :key LIMIT 1")
    suspend fun calendarByLocalKey(key: String): CalendarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalendar(calendar: CalendarEntity)

    @Query("DELETE FROM calendars WHERE id = :id")
    suspend fun deleteCalendar(id: String)

    @Query("UPDATE calendars SET visible = :visible WHERE id = :id")
    suspend fun setCalendarVisible(id: String, visible: Boolean)

    @Query("SELECT * FROM calendar_events WHERE start_at < :rangeEnd AND end_at >= :rangeStart ORDER BY start_at ASC")
    fun observeEventsInRange(rangeStart: Long, rangeEnd: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE calendar_id = :calendarId ORDER BY start_at ASC")
    fun observeEventsForCalendar(calendarId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun event(id: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE uid = :uid LIMIT 1")
    suspend fun eventByUid(uid: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE reminder_minutes IS NOT NULL AND start_at > :now ORDER BY start_at ASC")
    suspend fun upcomingWithReminders(now: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE sync_pending = 1")
    suspend fun pendingSync(): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("UPDATE calendar_events SET sync_pending = :pending, remote_href = :href, remote_etag = :etag WHERE id = :id")
    suspend fun markSyncState(id: String, pending: Boolean, href: String?, etag: String?)
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY display_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): ContactEntity?

    @Query(
        "SELECT * FROM contacts WHERE display_name LIKE '%' || :query || '%' " +
            "OR email LIKE '%' || :query || '%' ORDER BY display_name COLLATE NOCASE ASC",
    )
    suspend fun search(query: String): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contacts")
    suspend fun clear()

    @Query("SELECT * FROM contacts WHERE sync_pending = 1")
    suspend fun pendingSync(): List<ContactEntity>
}
