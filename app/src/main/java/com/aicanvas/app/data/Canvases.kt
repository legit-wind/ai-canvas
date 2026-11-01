package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A named screen of cards. Every card belongs to one canvas (by [id]). */
data class CanvasInfo(val id: String, val name: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
    companion object {
        fun fromJson(o: JSONObject) = o.optString("id").takeIf { it.isNotBlank() }?.let { CanvasInfo(it, o.optString("name").ifBlank { "Canvas" }) }
    }
}

/** Pure canvas logic (no Android), so it is unit-tested. */
object Canvases {
    const val HOME_ID = "home"
    val HOME = CanvasInfo(HOME_ID, "Home")

    /** Saved list, always starting with Home; a missing or broken list becomes just Home. */
    fun parse(raw: String?): List<CanvasInfo> {
        val list = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray()).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(CanvasInfo::fromJson) }
        }.distinctBy { it.id }
        return if (list.none { it.id == HOME_ID }) listOf(HOME) + list else list
    }

    fun serialize(list: List<CanvasInfo>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

    /** Cards saved before canvases existed (or pointing at a deleted canvas) go to Home. Nothing is dropped. */
    fun migrate(elements: List<Element>, canvases: List<CanvasInfo>): List<Element> {
        val ids = canvases.map { it.id }.toSet()
        return elements.map { if (it.canvas in ids) it else it.copy(canvas = HOME_ID) }
    }

    // ---------------- auto-organize ----------------
    val CATEGORIES = listOf("Academic", "Work", "Health", "Finance", "News & Info", "Personal")

    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "Academic" to listOf("exam", "timetable", "class", "lecture", "study", "assignment", "semester", "course", "syllabus", "quiz", "homework", "lab", "college", "school", "university", "grade", "revision", "ma20", "physics", "math"),
        "Work" to listOf("meeting", "project", "deadline", "client", "office", "standup", "sprint", "work", "email", "presentation", "report", "interview", "job"),
        "Health" to listOf("water", "exercise", "workout", "gym", "steps", "sleep", "medicine", "dose", "run", "yoga", "meditat", "calorie", "diet", "health", "walk", "pill", "weight"),
        "Finance" to listOf("expense", "budget", "spend", "₹", "$", "inr", "usd", "salary", "bill", "rent", "invest", "stock", "currency", "fx", "bank", "loan", "saving"),
        "News & Info" to listOf("news", "headline", "weather", "humidity", "forecast", "temperature", "briefing", "wiki", "convert", "exchange rate"),
    )

    /** Deterministic keyword classifier: the category with the most keyword hits, "Personal" when none match. */
    fun classify(text: String): String {
        val t = text.lowercase()
        val scores = KEYWORDS.mapValues { (_, words) -> words.count { it in t } }
        val best = scores.maxByOrNull { it.value }
        return if (best == null || best.value == 0) "Personal" else CATEGORIES.first { it == best.key }
    }

    /** All visible text in a card, for classifying. */
    fun textOf(ui: JSONObject): String {
        val sb = StringBuilder()
        fun walk(n: JSONObject) {
            for (k in listOf("text", "label", "title", "detail", "unit", "note", "kind", "subject", "day")) n.optString(k).takeIf { it.isNotBlank() }?.let { sb.append(it).append(' ') }
            for (k in listOf("children", "items", "days", "slots")) n.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(::walk) }
        }
        walk(ui)
        return sb.toString()
    }

    /** Reads the AI's {"id":"Category"} answer, keeping only known ids and categories (case-insensitive). */
    fun parseClassification(raw: String, ids: Set<String>, categories: List<String>): Map<String, String> {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyMap()
        val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyMap()
        return ids.mapNotNull { id ->
            val c = o.optString(id).trim()
            categories.firstOrNull { it.equals(c, ignoreCase = true) }?.let { id to it }
        }.toMap()
    }

    /** A canvas by id, or by name (case-insensitive); null when there's none. */
    fun resolve(canvases: List<CanvasInfo>, ref: String): CanvasInfo? {
        val r = ref.trim()
        if (r.isEmpty()) return null
        return canvases.firstOrNull { it.id == r } ?: canvases.firstOrNull { it.name.equals(r, ignoreCase = true) }
    }

    /** The result of merging canvases: cards moved onto [target], the other merged canvases gone. */
    data class Merged(val elements: List<Element>, val canvases: List<CanvasInfo>, val target: CanvasInfo)

    /**
     * Merges [ids] into one canvas called [name]. Home is kept as the target when it is one of them (so it can
     * never disappear); otherwise the first picked canvas is kept and renamed. Cards are only moved, never dropped.
     */
    fun merge(elements: List<Element>, canvases: List<CanvasInfo>, ids: List<String>, name: String): Merged? {
        val picked = ids.distinct().filter { id -> canvases.any { it.id == id } }
        if (picked.size < 2) return null
        val keep = if (HOME_ID in picked) HOME_ID else picked.first()
        val target = canvases.first { it.id == keep }.let { c -> c.copy(name = name.trim().ifBlank { c.name }) }
        val gone = picked.toSet() - keep
        return Merged(
            elements.map { if (it.canvas in gone) it.copy(canvas = keep) else it },
            canvases.filter { it.id !in gone }.map { if (it.id == keep) target else it },
            target,
        )
    }

    /** Back to a single canvas: every card on Home, every other canvas removed. */
    fun mergeAll(elements: List<Element>, canvases: List<CanvasInfo>): Merged {
        val home = canvases.firstOrNull { it.id == HOME_ID } ?: HOME
        return Merged(elements.map { it.copy(canvas = HOME_ID) }, listOf(home), home)
    }

    /**
     * Card ids an AI reply deletes: its "remove" ids plus "remove_all" scopes, where a scope is "all"/"everything"
     * or a canvas id or name ("current" meaning [current]). Unknown ids and scopes are ignored.
     */
    fun removalIds(elements: List<Element>, canvases: List<CanvasInfo>, remove: List<String>, removeAll: List<String>, current: String): List<String> {
        val ids = remove.filter { id -> elements.any { it.id == id } }.toMutableSet()
        removeAll.forEach { s ->
            when (val k = s.trim().lowercase()) {
                "all", "everything", "*" -> elements.forEach { ids += it.id }
                else -> {
                    val c = if (k == "current" || k == "this") current else resolve(canvases, s)?.id
                    if (c != null) elements.filter { it.canvas == c }.forEach { ids += it.id }
                }
            }
        }
        return elements.map { it.id }.filter { it in ids }
    }

    /** A planned move of one card to a category canvas. */
    data class Move(val elementId: String, val title: String, val from: String, val category: String)

    /** Proposed moves: each card to the canvas named after its category (only cards that would change place). */
    fun plan(elements: List<Element>, canvases: List<CanvasInfo>, classify: (Element) -> String = { classify(textOf(it.ui)) }): List<Move> =
        elements.mapNotNull { e ->
            val cat = classify(e)
            val current = canvases.firstOrNull { it.id == e.canvas }?.name
            if (current.equals(cat, ignoreCase = true)) null else Move(e.id, CardSummary.fold(e.ui).title, current ?: "Home", cat)
        }

    /** Finds the canvas for [name] or makes one; returns the (possibly longer) list and the canvas. */
    fun ensure(canvases: List<CanvasInfo>, name: String, newId: () -> String = ::newId): Pair<List<CanvasInfo>, CanvasInfo> {
        canvases.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return canvases to it }
        val c = CanvasInfo(newId(), name)
        return (canvases + c) to c
    }
}

/**
 * Live cards (weather, news, currency…) are refreshed in place. The spec says where fresh data comes from:
 * {"kind":"weather","query":"Hyderabad"}; [Element.refreshedAt] is when it was last refreshed.
 */
object Refresh {
    const val HOUR = 3_600_000L

    /** Weather and news go stale after 3 hours; everything else (daily routine, fx, briefing) after a day. */
    fun maxAge(kind: String): Long = when (kind) { "weather", "news" -> 3 * HOUR; else -> 24 * HOUR }

    fun isStale(spec: JSONObject?, refreshedAt: Long, now: Long): Boolean {
        if (spec == null || spec.optString("kind").isBlank()) return false
        if (refreshedAt <= 0L) return true
        return now - refreshedAt >= maxAge(spec.optString("kind"))
    }

    /** What the AI is asked when live cards are refreshed: rewrite only these cards, from real data only. */
    fun prompt(ids: List<String>): String =
        "[Refresh] Refresh these live cards in place with today's content: ${ids.joinToString(", ")}. Return them only in \"update\" " +
            "with the same ids; don't create or remove cards. Use only the lookup results below or what you can look up; if fresh " +
            "data isn't available, leave that card unchanged. Never make up numbers or headlines."

    /** "Updated 5 min ago" / "Updated 3 h ago" / "Updated 2 d ago". */
    fun ago(refreshedAt: Long, now: Long): String {
        val m = maxOf(0L, (now - refreshedAt) / 60_000)
        return when { m < 1 -> "Updated just now"; m < 60 -> "Updated $m min ago"; m < 1440 -> "Updated ${m / 60} h ago"; else -> "Updated ${m / 1440} d ago" }
    }

    /** Guesses a refresh spec for a card made before specs existed (or by an AI that left it out). Null = not live. */
    fun guess(ui: JSONObject, title: String?): JSONObject? {
        val text = ui.toString().lowercase()
        return when {
            "humidity" in text || "feels like" in text || "forecast" in text -> title?.let { JSONObject().put("kind", "weather").put("query", it) }
            "headline" in text || "news" in text -> JSONObject().put("kind", "news").put("query", "")
            "exchange rate" in text || Regex("""\b(usd|eur|gbp)\b.*\binr\b""").containsMatchIn(text) -> JSONObject().put("kind", "fx").put("query", title.orEmpty())
            "daily routine" in text || "briefing" in text || "quote of the day" in text -> JSONObject().put("kind", "daily").put("query", title.orEmpty())
            else -> null
        }
    }
}
