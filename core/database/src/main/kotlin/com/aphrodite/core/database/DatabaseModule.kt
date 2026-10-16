package com.aphrodite.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executor
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
}
