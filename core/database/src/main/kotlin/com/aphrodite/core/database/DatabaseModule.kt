package com.aphrodite.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aphrodite.core.database.dao.CalendarDao
import com.aphrodite.core.database.dao.ChatMessageDao
import com.aphrodite.core.database.dao.ContactDao
import com.aphrodite.core.database.dao.ModelDao
import com.aphrodite.core.database.dao.ModelEndpointDao
import com.aphrodite.core.database.dao.NoteDao
import com.aphrodite.core.database.dao.PresetDao
import com.aphrodite.core.database.dao.SessionDao
import com.aphrodite.core.database.dao.TaskDao
import com.aphrodite.core.database.dao.UploadDao
import com.aphrodite.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AphroditeDatabase =
        Room.databaseBuilder(context, AphroditeDatabase::class.java, AphroditeDatabase.NAME)
            // Full-text search over chat history is served by LIKE queries over indexed columns
            // today; enabling WAL keeps reads from blocking the writer during streaming writes.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Foreign keys are off by default in SQLite; the schema relies on them for
                    // cascade deletes of messages when a session is removed.
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()

    @Provides
    fun provideSessionDao(database: AphroditeDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideChatMessageDao(database: AphroditeDatabase): ChatMessageDao = database.chatMessageDao()

    @Provides
    fun provideUploadDao(database: AphroditeDatabase): UploadDao = database.uploadDao()

    @Provides
    fun provideModelEndpointDao(database: AphroditeDatabase): ModelEndpointDao = database.modelEndpointDao()

    @Provides
    fun provideModelDao(database: AphroditeDatabase): ModelDao = database.modelDao()

    @Provides
    fun providePresetDao(database: AphroditeDatabase): PresetDao = database.presetDao()

    @Provides
    fun provideNoteDao(database: AphroditeDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTaskDao(database: AphroditeDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideCalendarDao(database: AphroditeDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun provideContactDao(database: AphroditeDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideUserDao(database: AphroditeDatabase): UserDao = database.userDao()
}
