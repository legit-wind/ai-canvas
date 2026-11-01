package com.aicanvas.app.data

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** What a folded card shows: its title and one or two short lines with the most useful facts. */
object CardSummary {
    data class Folded(val title: String, val lines: List<String>)

    private fun walk(n: JSONObject, out: MutableList<JSONObject>) {
        out += n
        n.optJSONArray("children")?.let { k -> for (i in 0 until k.length()) k.optJSONObject(i)?.let { walk(it, out) } }
    }

    private fun nodes(ui: JSONObject) = mutableListOf<JSONObject>().also { walk(ui, it) }

    /**
     * Cards this small are shown whole; folding them would hide nothing. Small means a few blocks and no list,
     * chart or timetable.
     */
    fun isSmall(ui: JSONObject): Boolean {
        // Live module cards are always shown whole.
        if (ui.optString("type") == "module") return true
        val all = nodes(ui).filter { it.optString("type") != "stack" }
        if (all.any { it.optString("type") in setOf("list", "chart", "choice") }) return false
        if (all.count { it.optString("type") == "check" } > 3) return false
        return all.size <= 4
    }

    /** "📅 Final exams" → ("📅", "Final exams"); a title with no leading emoji → ("", title). */
    fun splitEmoji(title: String): Pair<String, String> {
        val t = title.trim()
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp)) break
            i += Character.charCount(cp)
        }
        val emoji = t.substring(0, i)
        return if (emoji.isEmpty() || emoji.all { it.code < 0x2000 }) "" to t else emoji to t.substring(i).trim().ifEmpty { t }
    }

    fun fold(ui: JSONObject, now: LocalDateTime = LocalDateTime.now()): Folded {
        val all = nodes(ui)
        val titleNode = all.firstOrNull { it.optString("type") == "text" && it.optString("style") == "title" }
            ?: all.firstOrNull { it.optString("type") == "text" }
        val schedule = all.firstOrNull { it.optString("type") == "list" && it.optString("kind") == "schedule" }
        if (ui.optString("type") == "module") return Folded(ui.optString("title").ifBlank { "Card" }, emptyList())
        val title = titleNode?.optString("text")?.trim().orEmpty().ifEmpty { schedule?.optString("title").orEmpty().ifEmpty { "Card" } }
        val lines = mutableListOf<String>()
        val checks = all.filter { it.optString("type") == "check" }
        var checksDone = false
        for (n in all) {
            if (lines.size >= 2) break
            if (n === titleNode) continue
            val line: String? = when (n.optString("type")) {
                "text" -> n.optString("text").trim().takeIf { it.isNotEmpty() && n.optString("style") != "label" }
                "check" -> if (checksDone) null else { checksDone = true; "✓ ${checks.count { it.optBoolean("checked") }} of ${checks.size} done" }
                "counter" -> listOfNotNull(
                    n.optString("label").ifBlank { null },
                    "${n.optInt("value")}" + (if (n.has("goal")) " / ${n.optInt("goal")}" else "") + n.optString("unit").let { if (it.isBlank()) "" else " $it" },
                ).joinToString(": ")
                "metric" -> "${n.optString("label")}: ${n.optString("value")}".trim(':', ' ')
                "progress", "ring" -> (n.optString("label").ifBlank { "${(n.optDouble("value", 0.0) * 100).toInt()}%" })
                "timer" -> if (n.has("to")) left(n.optString("to"), now) else "⏱ " + listOf(n.optString("label").ifBlank { "Timer" }, "${n.optInt("minutes")} min").joinToString(" · ")
                "list" -> if (n.optString("kind") == "schedule") nextClass(n, now) else firstItems(n)
                "choice" -> n.optString("question").ifBlank { null }
                else -> null
            }
            if (!line.isNullOrBlank() && line != title && line !in lines) lines += line
        }
        return Folded(title, lines)
    }

    private fun left(to: String, now: LocalDateTime): String? {
        val t = runCatching { LocalDateTime.parse(to.take(16)) }.getOrNull() ?: return null
        val m = ChronoUnit.MINUTES.between(now, t)
        if (m <= 0) return "⏳ Time's up"
        val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
        return "⏳ " + when { d > 0 -> "${d}d ${h}h left"; h > 0 -> "${h}h ${mm}m left"; else -> "${mm}m left" }
    }

    private fun firstItems(n: JSONObject): String? {
        val items = n.optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        val first = items.optJSONObject(0)?.optString("title") ?: items.optString(0)
        return first + if (items.length() > 1) " · +${items.length() - 1} more" else ""
    }

    private fun nextClass(n: JSONObject, now: LocalDateTime): String {
        val days = Schedule.parse(n)
        val i = Schedule.todayIndex(days, now.dayOfWeek)
        if (i < 0) return "No classes today"
        val day = days[i]
        val (slot, label) = Schedule.current(day, now.toLocalTime()) ?: return "Done for today"
        val s = day.slots[slot]
        return "$label: ${s.subject} at ${Schedule.clock(s.start)}"
    }
}
