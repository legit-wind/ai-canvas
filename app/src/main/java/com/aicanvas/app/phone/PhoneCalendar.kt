package com.aicanvas.app.phone

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The next week of the phone's own calendar, as short lines for the AI. Only used when the user turns it on. */
object PhoneCalendar {
    fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Lines like "Wed 28 Oct, 10:00 AM – 11:00 AM: DBMS lab" or "Thu 29 Oct (all day): Karaka Chaturthi". */
    fun upcoming(context: Context, days: Long = 7): List<String> =
        if (!granted(context)) emptyList() else range(context, LocalDate.now(), LocalDate.now().plusDays(days), 40)

    /** Events from [from] up to (not including) [to]; the range is capped at one year. */
    fun range(context: Context, from: LocalDate, to: LocalDate, max: Int = 150): List<String> {
        if (!granted(context)) return listOf("Calendar access is off. The user can turn on Use my calendar in Settings.")
        val zone = ZoneId.systemDefault()
        val (a, b) = clampRange(from, to)
        val start = a.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = b.atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val columns = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val day = DateTimeFormatter.ofPattern(if (a.year == b.year && a.year == LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy")
        val time = DateTimeFormatter.ofPattern("h:mm a")
        val lines = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && lines.size < max) {
                    val title = c.getString(0)?.trim().orEmpty().ifBlank { continue }
                    val allDay = c.getInt(3) == 1
                    // All-day events are stored at midnight UTC; timed ones in local time.
                    val begin = Instant.ofEpochMilli(c.getLong(1)).atZone(if (allDay) ZoneOffset.UTC else zone)
                    val finish = Instant.ofEpochMilli(c.getLong(2)).atZone(zone)
                    lines += if (allDay) "${begin.format(day)} (all day): $title"
                    else "${begin.format(day)}, ${begin.format(time)} – ${finish.format(time)}: $title"
                }
            }
        }
        return lines
    }

    /** Keeps a requested range sensible: [to] after [from], at most one year long. Pure, so it is unit-tested. */
    fun clampRange(from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate> {
        val end = if (to.isAfter(from)) to else from.plusDays(1)
        return from to minOf(end, from.plusYears(1))
    }

    /** Answers {"kind":"calendar","from":"YYYY-MM-DD","to":"YYYY-MM-DD"} for the AI. */
    fun lookup(context: Context, request: org.json.JSONObject): String {
        val from = runCatching { LocalDate.parse(request.optString("from")) }.getOrDefault(LocalDate.now())
        val to = runCatching { LocalDate.parse(request.optString("to")) }.getOrDefault(from.plusDays(7))
        val lines = range(context, from, to)
        return if (lines.isEmpty()) "No events from $from to $to (end not included)." else lines.joinToString("\n")
    }
}
