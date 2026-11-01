package com.aicanvas.app.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A day to count down to (an exam, a trip). [date] is ISO yyyy-MM-dd. */
data class Countdown(val id: String = newId(), val title: String, val date: String, val emoji: String = "📅") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("date", date).put("emoji", emoji)

    companion object {
        fun fromJson(o: JSONObject): Countdown? {
            val title = o.optString("title").trim().ifEmpty { return null }
            val date = o.optString("date").takeIf { Tasks.parseDate(it) != null } ?: return null
            return Countdown(o.optString("id").ifEmpty { newId() }, title, date, o.optString("emoji").ifBlank { "📅" })
        }
    }
}

object Countdowns {
    fun daysLeft(c: Countdown, today: LocalDate): Long = ChronoUnit.DAYS.between(today, LocalDate.parse(c.date))

    fun label(days: Long): String = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days > 1 -> "in $days days"
        days == -1L -> "Yesterday"
        else -> "${-days} days ago"
    }

    /** Upcoming soonest first, then past ones most recent first. */
    fun sorted(all: List<Countdown>, today: LocalDate): List<Countdown> {
        val (future, past) = all.partition { daysLeft(it, today) >= 0 }
        return future.sortedBy { daysLeft(it, today) } + past.sortedByDescending { daysLeft(it, today) }
    }
}
