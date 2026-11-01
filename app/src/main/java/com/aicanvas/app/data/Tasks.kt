package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A to-do item in the Tasks page. [due] is an ISO date (yyyy-MM-dd) or null. [repeat] is none/daily/weekly/monthly. */
data class TaskItem(
    val id: String = newId(),
    val title: String,
    val notes: String = "",
    val due: String? = null,
    val priority: Int = 2, // 1 high, 2 normal, 3 low
    val category: String = "",
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val repeat: String = "none",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("notes", notes); due?.let { put("due", it) }
        put("priority", priority); put("category", category); put("tags", JSONArray(tags))
        put("subtasks", JSONArray(subtasks.map { JSONObject().put("t", it.title).put("d", it.done) }))
        put("repeat", repeat); put("done", done); put("createdAt", createdAt); doneAt?.let { put("doneAt", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): TaskItem? {
            val title = o.optString("title").trim().ifEmpty { return null }
            val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val subs = o.optJSONArray("subtasks")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Subtask(it.optString("t"), it.optBoolean("d")) }
            } ?: emptyList()
            return TaskItem(
                id = o.optString("id").ifEmpty { newId() }, title = title, notes = o.optString("notes"),
                due = o.optString("due").takeIf { Tasks.parseDate(it) != null },
                priority = o.optInt("priority", 2).coerceIn(1, 3), category = o.optString("category"),
                tags = tags.filter { it.isNotBlank() }, subtasks = subs,
                repeat = o.optString("repeat", "none").takeIf { it in Tasks.REPEATS } ?: "none",
                done = o.optBoolean("done"), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                doneAt = if (o.has("doneAt")) o.optLong("doneAt") else null,
            )
        }
    }
}

data class Subtask(val title: String, val done: Boolean = false)

/** Pure task logic (views, search, sorting, recurring), kept here so it can be unit-tested. */
object Tasks {
    val REPEATS = listOf("none", "daily", "weekly", "monthly")
    val VIEWS = listOf("all", "today", "upcoming", "priority", "completed")

    fun parseDate(s: String?): LocalDate? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    fun isOverdue(t: TaskItem, today: LocalDate) = !t.done && parseDate(t.due)?.isBefore(today) == true

    /** Open tasks first: overdue, then by due date (no date last), then priority, then newest. */
    private val order = compareBy<TaskItem>({ it.done }, { parseDate(it.due) ?: LocalDate.MAX }, { it.priority }, { -it.createdAt })

    fun view(all: List<TaskItem>, view: String, query: String, today: LocalDate): List<TaskItem> {
        val q = query.trim().lowercase()
        val found = if (q.isEmpty()) all else all.filter { t ->
            listOf(t.title, t.notes, t.category).any { it.lowercase().contains(q) } ||
                t.tags.any { it.lowercase().contains(q) } || t.subtasks.any { it.title.lowercase().contains(q) }
        }
        return when (view) {
            "today" -> found.filter { !it.done && parseDate(it.due)?.let { d -> !d.isAfter(today) } == true }.sortedWith(order)
            "upcoming" -> found.filter { !it.done && parseDate(it.due)?.isAfter(today) == true }.sortedWith(order)
            "priority" -> found.filter { !it.done }.sortedWith(compareBy<TaskItem>({ it.priority }).then(order))
            "completed" -> found.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
            else -> found.filter { !it.done }.sortedWith(order)
        }
    }

    /**
     * Ticks a task. A repeating task with a due date isn't closed: it moves to its next date and its subtasks reset.
     * Returns the updated task.
     */
    fun complete(t: TaskItem, now: Long = System.currentTimeMillis()): TaskItem {
        if (t.done) return t.copy(done = false, doneAt = null)
        val due = parseDate(t.due)
        if (t.repeat != "none" && due != null) {
            val next = when (t.repeat) { "daily" -> due.plusDays(1); "weekly" -> due.plusWeeks(1); else -> due.plusMonths(1) }
            return t.copy(due = next.toString(), subtasks = t.subtasks.map { it.copy(done = false) })
        }
        return t.copy(done = true, doneAt = now)
    }

    fun progress(t: TaskItem): Pair<Int, Int> = t.subtasks.count { it.done } to t.subtasks.size

    fun parseTags(s: String) = s.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
