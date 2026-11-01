package com.aicanvas.app.phone

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aicanvas.app.MainActivity
import com.aicanvas.app.R
import com.aicanvas.app.data.Store
import java.time.Instant
import java.time.ZoneId

/** Phone notifications at a set time, even when the app is closed. Daily and weekly ones repeat. */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val HOUR = 60L * 60 * 1000
    private const val DAY = 24 * HOUR
    const val ACTION_SNOOZE = "com.aicanvas.app.SNOOZE"
    private const val SNOOZE_MINUTES = 10

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * The first time after [now] that a reminder repeating from [at] goes off. "hours:N" reminders skip the
     * quiet night hours (10 PM to 8 AM).
     */
    fun nextAfter(at: Long, repeat: String, now: Long): Long {
        val hours = if (repeat.startsWith("hours:")) repeat.removePrefix("hours:").toIntOrNull()?.coerceIn(1, 12) else null
        if (hours != null) {
            val zone = ZoneId.systemDefault()
            var t = at
            var steps = 0
            while ((t <= now || Instant.ofEpochMilli(t).atZone(zone).hour !in 8..21) && steps++ < 10_000) t += hours * HOUR
            return t
        }
        val period = if (repeat == "weekly") 7 * DAY else DAY
        var t = at
        while (t <= now) t += period
        return t
    }

    fun schedule(context: Context, id: String, at: Long, text: String) {
        runCatching { Store(context).lastZone = ZoneId.systemDefault().id }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, id, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id, ""))
    }

    private fun pending(context: Context, id: String, text: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** [body] adds lines under the title (the morning briefing uses it). */
    fun show(context: Context, id: String, text: String, body: String? = null) {
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            (id + ACTION_SNOOZE).hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("id", id).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(text)
            .setContentIntent(open)
            .apply {
                if (body != null) {
                    setContentText(body.lineSequence().firstOrNull())
                    setStyle(NotificationCompat.BigTextStyle().bigText(body))
                } else {
                    addAction(0, "Snooze $SNOOZE_MINUTES min", snooze)
                }
            }
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }

    /** Puts the notification away and shows it again in a few minutes. */
    fun snooze(context: Context, id: String, text: String) {
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        schedule(context, "$id-snoozed", System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, text)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val text = intent.getStringExtra("text").orEmpty().ifBlank { "Reminder" }
        if (intent.action == Reminders.ACTION_SNOOZE) return Reminders.snooze(context, id, text)
        if (id == Briefing.ID) {
            Briefing.show(context)
            Briefing.schedule(context)
            return
        }
        Reminders.show(context, id, text)
        val store = Store(context)
        val all = store.loadReminders()
        val fired = all.find { it.id == id }
        if (fired != null && fired.repeat.isNotEmpty()) {
            // Repeating: book the next one and keep it in the list.
            val next = fired.copy(at = Reminders.nextAfter(fired.at, fired.repeat, System.currentTimeMillis()))
            Reminders.schedule(context, next.id, next.at, next.text)
            store.saveReminders(all.map { if (it.id == id) next else it })
        } else {
            store.saveReminders(all.filter { it.id != id })
        }
    }
}

/**
 * Alarms are forgotten on reboot and after an app update, and a time or zone change moves them. Book the
 * upcoming reminders and running timers again, keep clock times across a zone change, and show one-off
 * reminders that were missed while the phone was off (up to 12 hours late) marked as missed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val now = System.currentTimeMillis()
        Briefing.schedule(context)
        val store = Store(context)
        store.loadElements().forEach { Timers.scheduleRunning(context, it, now) }
        val zone = ZoneId.systemDefault()
        val oldZone = runCatching { ZoneId.of(store.lastZone) }.getOrNull()
        val all = store.loadReminders().map { r ->
            if (oldZone != null && oldZone != zone) r.copy(at = com.aicanvas.app.data.shiftForZone(r.at, oldZone, zone)) else r
        }
        all.forEach { Reminders.cancel(context, it.id) }
        val (keep, missed) = com.aicanvas.app.data.ReminderPlan.afterRestart(all, now) { Reminders.nextAfter(it.at, it.repeat, now) }
        keep.forEach { Reminders.schedule(context, it.id, it.at, it.text) }
        missed.forEach { Reminders.show(context, it.id, "Missed: ${it.text}") }
        store.saveReminders(keep)
        store.lastZone = zone.id
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
