package com.aphrodite.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Note reminders.
 *
 * A reminder used to be a timestamp on a note with nothing behind it. This schedules an alarm that
 * survives the app being closed, and if exact alarms are not permitted — Android 14 makes them a
 * user-granted permission — it falls back to an inexact alarm rather than silently not firing.
 */
@Singleton
class NoteReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** Schedules (or replaces) the reminder for [noteId] at [atMillis]. */
    fun schedule(noteId: String, title: String, atMillis: Long) {
        val manager = alarmManager ?: return
        // A reminder in the past is not worth scheduling; the note keeps the timestamp so the
        // user can see it was missed.
        if (atMillis <= System.currentTimeMillis()) return

        ensureChannel(context)
        val pending = pendingIntent(noteId, title, atMillis)

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        runCatching {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        }
    }

    fun cancel(noteId: String) {
        val manager = alarmManager ?: return
        manager.cancel(pendingIntent(noteId, "", 0))
    }

    private fun pendingIntent(noteId: String, title: String, atMillis: Long): PendingIntent {
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            action = ACTION_NOTE_REMINDER
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_TITLE, title)
            // Distinct data per note so two reminders do not replace each other's PendingIntent.
            data = android.net.Uri.parse("aphrodite://note/$noteId")
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            // IMMUTABLE is required from Android 12; nothing needs to fill this in.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_NOTE_REMINDER = "com.aphrodite.action.NOTE_REMINDER"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "title"
        const val CHANNEL_REMINDERS = "reminders"

        /** Creates the notification channel. Safe to call repeatedly. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Note reminders"
            }
            manager.createNotificationChannel(channel)
        }
    }
}

/** Delivers a note reminder as a notification. */
class NoteReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NoteReminderScheduler.ACTION_NOTE_REMINDER) return

        val noteId = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(NoteReminderScheduler.EXTRA_TITLE).orEmpty()
        NoteReminderScheduler.ensureChannel(context)

        // Without the runtime permission the notification cannot be posted; nothing useful can be
        // done from a receiver, so fail quietly rather than crashing in the background.
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!permitted) return

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NoteReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val contentIntent = open?.let {
            PendingIntent.getActivity(
                context,
                noteId.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, NoteReminderScheduler.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.ifBlank { "Note reminder" })
            .setContentText("Tap to open the note")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(noteId.hashCode(), notification)
        }
    }
}
