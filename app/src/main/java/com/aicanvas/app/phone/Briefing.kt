package com.aicanvas.app.phone

import android.content.Context
import com.aicanvas.app.data.Store
import com.aicanvas.app.data.byImportance
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A morning notification listing the most important cards ("DBMS exam · in 3h", "Water · 2 / 8 glasses").
 * Made on the phone from what is on screen: no AI call, works offline.
 */
object Briefing {
    const val ID = "briefing"

    /** Turns the briefing on at [time] ("HH:mm"), or off when blank. */
    fun set(context: Context, time: String) {
        Store(context).briefingTime = time
        if (time.isBlank()) Reminders.cancel(context, ID) else schedule(context)
    }

    /** Books the next briefing, if it is on. */
    fun schedule(context: Context) {
        val time = runCatching { LocalTime.parse(Store(context).briefingTime) }.getOrNull() ?: return
        var at = LocalDate.now().atTime(time)
        if (!at.isAfter(java.time.LocalDateTime.now())) at = at.plusDays(1)
        Reminders.schedule(context, ID, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "")
    }

    /** Shows the briefing now. [title] is "Good morning" on schedule, or a preview title. */
    fun show(context: Context, title: String = "Good morning") {
        val lines = Store(context).loadElements().sortedWith(byImportance).take(4)
            .map { HomeWidget.summarize(it) }
            .filter { it.first.isNotBlank() }
            .map { (t, d) -> if (d.isBlank()) t else "$t · $d" }
        val body = lines.joinToString("\n").ifBlank { "Your screen is clear. Tell AI Canvas what's on today." }
        Reminders.show(context, ID, title, body)
    }

    fun label(time: String): String =
        runCatching { LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault("Off")
}
