package com.aicanvas.app.data

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * A weekly timetable drawn by a `list` with `"kind":"schedule"`:
 * {"type":"list","kind":"schedule","title":"Week","days":[{"day":"MON","slots":[{"time":"8:00-8:50","subject":"AI"}]}]}
 * Pure logic (no Compose), so it is unit-tested.
 */
object Schedule {
    data class Slot(val start: Int, val end: Int, val subject: String) {
        val free get() = subject.isEmpty()
        val time get() = "${clock(start)}-${clock(end)}"
    }
    data class Day(val name: String, val slots: List<Slot>)

    val TONES = listOf("blue", "green", "amber", "rose", "violet", "teal")
    private val FREE = setOf("", "free", "-", "—", "break", "none")

    /** Days in order, each with free/repeated neighbouring slots merged and trailing free time dropped. */
    fun parse(n: JSONObject): List<Day> {
        val days = n.optJSONArray("days") ?: return emptyList()
        return (0 until days.length()).mapNotNull { days.optJSONObject(it) }.map { d ->
            val arr = d.optJSONArray("slots")
            val raw = (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it) }.mapNotNull { s ->
                val (a, b) = range(s.optString("time")) ?: return@mapNotNull null
                val subj = s.optString("subject").trim()
                Slot(a, b, if (subj.lowercase() in FREE) "" else subj)
            }.sortedBy { it.start }
            Day(d.optString("day").trim().uppercase().take(3), merge(raw))
        }
    }

    /** Joins a lab or free period that spans several slots into one row with the full time range. */
    fun merge(slots: List<Slot>): List<Slot> {
        val out = mutableListOf<Slot>()
        for (s in slots) {
            val last = out.lastOrNull()
            if (last != null && last.subject.equals(s.subject, ignoreCase = true) && s.start - last.end <= 15) out[out.size - 1] = last.copy(end = s.end)
            else out += s
        }
        while (out.lastOrNull()?.free == true) out.removeAt(out.size - 1)
        return out
    }

    /** "8:00-8:50" → minutes from midnight. School-day hours 1–6 without AM/PM are read as afternoon. */
    fun range(t: String): Pair<Int, Int>? {
        val m = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?\s*[-–to]+\s*(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE).find(t) ?: return null
        fun min(h: String, mm: String, ap: String): Int {
            var hour = h.toInt() % 24
            when (ap.lowercase()) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
                else -> if (hour in 1..6) hour += 12
            }
            return hour * 60 + (mm.ifEmpty { "0" }).toInt()
        }
        val g = m.groupValues
        val a = min(g[1], g[2], g[3]); val b = min(g[4], g[5], g[6].ifEmpty { g[3] })
        return if (b > a) a to b else null
    }

    fun clock(min: Int): String { val h = (min / 60) % 12; return "${if (h == 0) 12 else h}:${"%02d".format(min % 60)}" }

    /** Index of [day] in [days] by its three-letter name, or -1 (weekend or missing). */
    fun todayIndex(days: List<Day>, day: DayOfWeek): Int = days.indexOfFirst { it.name == day.name.take(3) }

    /** The class happening at [now] ("now") or the next one today ("next"), as a slot index; null if done. */
    fun current(day: Day, now: LocalTime): Pair<Int, String>? {
        val m = now.hour * 60 + now.minute
        day.slots.forEachIndexed { i, s -> if (!s.free && m in s.start until s.end) return i to "Now" }
        day.slots.forEachIndexed { i, s -> if (!s.free && s.start > m) return i to "Next" }
        return null
    }

    /** "DSP LAB" and "DSP" share a colour; subjects get the six tones in alphabetical order so the week reads clearly. */
    fun base(subject: String) = subject.uppercase().replace(Regex("""\b(LAB|PRACTICAL|TUTORIAL|TUT)\b"""), "").trim()

    fun tones(days: List<Day>): Map<String, String> {
        val subjects = days.flatMap { d -> d.slots.filter { !it.free }.map { base(it.subject) } }.filter { it != "LUNCH" }.distinct().sorted()
        return subjects.withIndex().associate { (i, s) -> s to TONES[i % TONES.size] }
    }

    /** One line for a day: "AI · CS · CE · VLSI · DSP LAB". */
    fun summary(day: Day) = day.slots.filter { !it.free }.joinToString(" · ") { it.subject }.ifEmpty { "No classes" }

    private val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private fun arr(o: JSONObject, vararg keys: String) = keys.firstNotNullOfOrNull { o.optJSONArray(it) }
    private fun str(o: JSONObject, vararg keys: String) = keys.firstNotNullOfOrNull { k -> o.optString(k).trim().takeIf { it.isNotEmpty() } }.orEmpty()

    /**
     * Rewrites timetables the AI wrote in other shapes into the expandable schedule block, in place:
     * a "schedule"/"timetable" node, a list with "days", or list rows named after weekdays that carry their slots
     * ("slots", "classes", "periods" or "items"), with "start"/"end" or "time", "subject"/"name"/"title".
     */
    fun adopt(n: JSONObject) {
        when (n.optString("type")) {
            "schedule", "timetable" -> { n.put("type", "list"); n.put("kind", "schedule") }
            "list" -> when {
                n.has("days") -> n.put("kind", "schedule")
                n.optString("kind") != "schedule" -> {
                    val items = n.optJSONArray("items")
                    val rows = (0 until (items?.length() ?: 0)).mapNotNull { items!!.optJSONObject(it) }
                    val dayRows = rows.filter { r -> str(r, "title", "day").uppercase().take(3) in WEEKDAYS && arr(r, "slots", "classes", "periods", "items") != null }
                    if (rows.isNotEmpty() && dayRows.size == rows.size) {
                        n.put("kind", "schedule")
                        n.put("days", org.json.JSONArray().apply { rows.forEach { put(it) } })
                        n.remove("items")
                    }
                }
            }
        }
        if (n.optString("kind") == "schedule") {
            val days = n.optJSONArray("days") ?: return
            for (i in 0 until days.length()) {
                val d = days.optJSONObject(i) ?: continue
                d.put("day", str(d, "day", "name", "title"))
                val slots = arr(d, "slots", "classes", "periods", "items") ?: org.json.JSONArray()
                for (j in 0 until slots.length()) {
                    val sl = slots.optJSONObject(j) ?: continue
                    if (sl.optString("time").isBlank()) {
                        val a = str(sl, "start", "from"); val b = str(sl, "end", "to")
                        if (a.isNotEmpty() && b.isNotEmpty()) sl.put("time", "$a-$b")
                    }
                    if (sl.optString("subject").isBlank()) sl.put("subject", str(sl, "name", "title", "class", "course"))
                }
                d.put("slots", slots)
                listOf("classes", "periods", "items").forEach { d.remove(it) }
            }
            return
        }
        n.optJSONArray("children")?.let { k -> for (i in 0 until k.length()) k.optJSONObject(i)?.let { adopt(it) } }
    }

    /** True when 3+ rows are named after weekdays: a timetable squeezed into plain rows (with no class slots). */
    fun looksLikeDays(items: org.json.JSONArray): Boolean {
        val rows = (0 until items.length()).mapNotNull { items.optJSONObject(it) }.filter { str(it, "title", "day").uppercase().take(3) in WEEKDAYS }
        val span = Regex("""\d{1,2}[:.]\d{2}.*[-–to]+.*\d{1,2}[:.]\d{2}""")
        return rows.size >= 3 && rows.any { r -> span.containsMatchIn(r.optString("meta") + " " + r.optString("detail")) }
    }

    /** Applies [adopt] to every design in an AI answer. */
    fun adoptAll(reply: JSONObject) {
        for (key in listOf("create", "update")) {
            val a = reply.optJSONArray(key) ?: continue
            for (i in 0 until a.length()) a.optJSONObject(i)?.optJSONObject("ui")?.let { adopt(it) }
        }
    }
}
