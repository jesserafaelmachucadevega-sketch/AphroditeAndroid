package com.aphrodite.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aphrodite.core.database.dao.CalendarDao
import com.aphrodite.core.database.dao.ChatMessageDao
import com.aphrodite.core.database.dao.ContactDao
import com.aphrodite.core.database.dao.ModelEndpointDao
import com.aphrodite.core.database.dao.ModelDao
import com.aphrodite.core.database.dao.NoteDao
import com.aphrodite.core.database.dao.PresetDao
import com.aphrodite.core.database.dao.SessionDao
import com.aphrodite.core.database.dao.TaskDao
import com.aphrodite.core.database.dao.UploadDao
import com.aphrodite.core.database.entity.CalendarAccountEntity
import com.aphrodite.core.database.entity.CalendarEntity
import com.aphrodite.core.database.entity.CalendarEventEntity
import com.aphrodite.core.database.entity.ChatAttachmentEntity
import com.aphrodite.core.database.entity.ChatMessageEntity
import com.aphrodite.core.database.entity.ContactEntity
import com.aphrodite.core.database.entity.ModelEndpointEntity
import com.aphrodite.core.database.entity.ModelEntity
import com.aphrodite.core.database.entity.NoteEntity
import com.aphrodite.core.database.entity.NoteItemEntity
import com.aphrodite.core.database.entity.PresetEntity
import com.aphrodite.core.database.entity.PresetGroupEntity
import com.aphrodite.core.database.entity.PresetTemplateEntity
import com.aphrodite.core.database.entity.SessionEntity
import com.aphrodite.core.database.entity.TaskEntity
import com.aphrodite.core.database.entity.TaskRunEntity
import com.aphrodite.core.database.entity.UploadEntity

/** The local store. Every user-visible record lives here; nothing is server-owned. */
@Database(
    entities = [
        SessionEntity::class,
        ChatMessageEntity::class,
        ChatAttachmentEntity::class,
        UploadEntity::class,
        ModelEndpointEntity::class,
        ModelEntity::class,
        PresetEntity::class,
        PresetTemplateEntity::class,
        PresetGroupEntity::class,
        NoteEntity::class,
        NoteItemEntity::class,
        TaskEntity::class,
        TaskRunEntity::class,
        CalendarAccountEntity::class,
        CalendarEntity::class,
        CalendarEventEntity::class,
        ContactEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AphroditeDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun uploadDao(): UploadDao
    abstract fun modelEndpointDao(): ModelEndpointDao
    abstract fun modelDao(): ModelDao
    abstract fun presetDao(): PresetDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarDao(): CalendarDao
    abstract fun contactDao(): ContactDao

    companion object {
        const val NAME = "aphrodite.db"
    }
}
