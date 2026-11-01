package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A habit. [days] are ISO weekdays (1 = Monday … 7 = Sunday) it is due on; [done] holds ISO dates it was done. */
data class Habit(
    val id: String = newId(),
    val name: String,
    val emoji: String = "✅",
    val days: Set<Int> = (1..7).toSet(),
    val done: Set<String> = emptySet(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("emoji", emoji)
        .put("days", JSONArray(days.sorted())).put("done", JSONArray(done.sorted()))

    companion object {
        fun fromJson(o: JSONObject): Habit? {
            val name = o.optString("name").trim().ifEmpty { return null }
            val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.toSet() }
                ?.ifEmpty { null } ?: (1..7).toSet()
            val done = o.optJSONArray("done")?.let { a -> (0 until a.length()).map { a.optString(it) }
                .filter { Tasks.parseDate(it) != null }.toSet() } ?: emptySet()
            return Habit(o.optString("id").ifEmpty { newId() }, name, o.optString("emoji").ifBlank { "✅" }, days, done)
        }
    }
}

/** Habit maths on local calendar dates (the phone's own time zone), so streaks don't break at midnight UTC. */
object Habits {
    fun isDue(h: Habit, d: LocalDate) = d.dayOfWeek.value in h.days

    fun toggle(h: Habit, d: LocalDate): Habit = d.toString().let { if (it in h.done) h.copy(done = h.done - it) else h.copy(done = h.done + it) }

    /**
     * Current streak: due days done in a row, counting back from today. Today not done yet doesn't break it
     * (the day isn't over). Days the habit isn't scheduled are skipped.
     */
    fun streak(h: Habit, today: LocalDate): Int {
        var d = today
        if (d.toString() !in h.done) d = d.minusDays(1)
        var n = 0
        repeat(366 * 3) {
            if (isDue(h, d) || d.toString() in h.done) {
                if (d.toString() in h.done) n++ else return n
            }
            d = d.minusDays(1)
        }
        return n
    }

    fun best(h: Habit): Int {
        val dates = h.done.mapNotNull { Tasks.parseDate(it) }.sorted()
        if (dates.isEmpty()) return 0
        var best = 0; var run = 0; var d = dates.first(); val end = dates.last()
        while (!d.isAfter(end)) {
            if (d.toString() in h.done) { run++; best = maxOf(best, run) } else if (isDue(h, d)) run = 0
            d = d.plusDays(1)
        }
        return best
    }

    /** Done due-days ÷ due-days over the last [days] days (including today), or null when none were due. */
    fun rate(h: Habit, today: LocalDate, days: Int = 30): Pair<Int, Int>? {
        var due = 0; var done = 0
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            if (isDue(h, d)) { due++; if (d.toString() in h.done) done++ }
        }
        return if (due == 0) null else done to due
    }
}
