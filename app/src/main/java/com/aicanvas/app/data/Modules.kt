package com.aicanvas.app.data

import org.json.JSONObject

/**
 * Live home cards for the app's own data (tasks, habits, countdowns, expenses). The card's ui is just
 * {"type":"module","module":kind,"title":…}; it's drawn from the live lists, so edits on either side show at once.
 */
object Modules {
    val KINDS = listOf("tasks", "habits", "countdowns", "expenses")

    fun title(kind: String) = when (kind) {
        "tasks" -> "✅ Today's tasks"
        "habits" -> "🔥 Habits today"
        "countdowns" -> "⏳ Countdowns"
        else -> "💸 Expenses"
    }

    fun ui(kind: String): JSONObject = JSONObject().put("type", "module").put("module", kind).put("title", title(kind))

    /** The module kind a card shows, or null for an ordinary card. */
    fun kindOf(ui: JSONObject?): String? = ui?.takeIf { it.optString("type") == "module" }?.optString("module")?.takeIf { it in KINDS }

    /**
     * Which kinds need a new home card: there's data for it, auto-cards are on, no card for it exists on any
     * canvas, and the user hasn't removed it.
     */
    fun missing(hasData: Map<String, Boolean>, existing: List<JSONObject>, hidden: Set<String>, enabled: Boolean): List<String> {
        if (!enabled) return emptyList()
        val have = existing.mapNotNull(::kindOf).toSet()
        return KINDS.filter { hasData[it] == true && it !in have && it !in hidden }
    }
}
