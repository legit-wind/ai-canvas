package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Checks card designs that come from the AI before they are saved or drawn, and carries the user's
 * own progress (ticks, counter values, running timers) over when the AI redesigns a card.
 */
object UiSchema {
    /** Node types the renderer knows how to draw. */
    /** The twelve building blocks the AI is taught (see Ai.SYSTEM). */
    val PRIMITIVES = setOf("stack", "text", "check", "counter", "progress", "chart", "timer", "input", "button", "metric", "list", "choice")
    /** Older types still drawn so cards saved by earlier versions keep working. The AI is not taught these. */
    val LEGACY = setOf("column", "row", "box", "ring", "countdown", "chip", "emoji", "divider", "spacer")
    val TYPES = PRIMITIVES + LEGACY
    val CONTAINERS = setOf("stack", "column", "row", "box")
    const val MAX_DEPTH = 12
    const val MAX_NODES = 400
    const val MAX_TEXT = 2_000

    /**
     * Returns a cleaned copy of [ui], or null when it can't be drawn (not an object, unknown root type,
     * or too big). Unknown or broken child nodes are dropped rather than failing the whole card.
     */
    fun sanitize(ui: Any?): JSONObject? {
        if (ui !is JSONObject) return null
        val budget = intArrayOf(MAX_NODES)
        return clean(ui, 0, budget)?.also { normalize(it) }
    }

    /**
     * Rewrites the public building blocks into the forms the renderer draws: stack → column/row,
     * progress with shape ring → ring, timer with a target date → countdown.
     */
    fun normalize(n: JSONObject) {
        when (n.optString("type")) {
            "stack" -> n.put("type", if (n.optString("direction") == "row") "row" else "column")
            "progress" -> if (n.optString("shape") == "ring") n.put("type", "ring")
            "timer" -> if (n.optString("to").isNotBlank()) n.put("type", "countdown")
        }
        val kids = n.optJSONArray("children") ?: return
        for (i in 0 until kids.length()) kids.optJSONObject(i)?.let { normalize(it) }
    }

    private fun clean(node: JSONObject, depth: Int, budget: IntArray): JSONObject? {
        val type = node.optString("type")
        if (type !in TYPES || depth > MAX_DEPTH || budget[0]-- <= 0) return null
        val out = JSONObject()
        for (key in node.keys()) {
            when (val v = node.opt(key)) {
                is String -> out.put(key, if (v.length > MAX_TEXT) v.take(MAX_TEXT) else v)
                is Number -> { val d = v.toDouble(); if (!d.isNaN() && !d.isInfinite()) out.put(key, v) }
                is Boolean -> out.put(key, v)
                is JSONArray -> if (key != "children") out.put(key, v)
                is JSONObject -> out.put(key, v)
                else -> Unit // JSONObject.NULL and anything else
            }
        }
        if (type in CONTAINERS) {
            val kids = JSONArray()
            node.optJSONArray("children")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { clean(it, depth + 1, budget) }?.let { kids.put(it) }
                }
            }
            out.put("children", kids)
        }
        return out
    }

    /**
     * Copies what the user did in [old] into [new]: ticked items, counter values and timer state, matched by
     * node type and label. Returns [new] (changed in place).
     */
    fun keepUserState(old: JSONObject, new: JSONObject): JSONObject {
        val state = HashMap<String, JSONObject>()
        walk(old) { n -> keyOf(n)?.let { state.putIfAbsent(it, n) } }
        walk(new) { n ->
            val before = keyOf(n)?.let { state[it] } ?: return@walk
            when (n.optString("type")) {
                "check" -> n.put("checked", before.optBoolean("checked"))
                "counter" -> if (before.has("value")) n.put("value", before.optDouble("value", 0.0))
                "choice" -> if (before.has("picked")) n.put("picked", before.optInt("picked"))
                "timer" -> {
                    if (before.has("sessions")) n.put("sessions", before.optInt("sessions")).put("counted", before.optBoolean("counted"))
                    n.put("endsAt", before.optLong("endsAt"))
                    n.put("left", before.optLong("left"))
                    n.remove("start")
                }
            }
            // Logs the phone keeps (7-day history) survive a redesign.
            before.optJSONArray("history")?.let { if (!n.has("history")) n.put("history", it) }
        }
        return new
    }

    private fun keyOf(n: JSONObject): String? {
        val type = n.optString("type")
        if (type != "check" && type != "counter" && type != "timer" && type != "choice") return null
        val label = (if (type == "choice") n.optString("question") else n.optString("label")).trim().lowercase()
        return if (label.isEmpty()) null else "$type:$label"
    }

    private fun walk(n: JSONObject, block: (JSONObject) -> Unit) {
        block(n)
        val kids = n.optJSONArray("children") ?: return
        for (i in 0 until kids.length()) kids.optJSONObject(i)?.let { walk(it, block) }
    }
}
