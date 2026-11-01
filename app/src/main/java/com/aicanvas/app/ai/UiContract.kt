package com.aicanvas.app.ai

import com.aicanvas.app.data.UiSchema
import org.json.JSONArray
import org.json.JSONObject

/**
 * Checks the model's whole answer before anything reaches the canvas. Returns a short list of problems
 * (empty when the answer is fine). The problems are sent back to the model once so it can repair them.
 * Pure Kotlin + org.json, so it is unit-tested.
 */
object UiContract {
    private val ACTIONS = listOf("reply", "create", "update", "remove", "remember", "forget", "remind", "cancel_reminders", "lookup")
    const val MAX_DEPTH = 8
    const val MAX_NODES = 120
    const val MAX_ACTIONS = 30

    fun problems(reply: JSONObject?): List<String> {
        if (reply == null) return listOf("The answer is not one valid JSON object")
        val out = mutableListOf<String>()
        if (ACTIONS.none { reply.has(it) }) out += "Missing \"reply\" or any canvas action"
        if (reply.has("reply") && reply.opt("reply") !is String) out += "\"reply\" must be text"
        for (key in ACTIONS.drop(1)) {
            if (!reply.has(key)) continue
            val arr = reply.opt(key) as? JSONArray
            if (arr == null) { out += "\"$key\" must be a list"; continue }
            if (arr.length() > MAX_ACTIONS) out += "Too many items in \"$key\""
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                when (key) {
                    "create", "update" -> {
                        if (item !is JSONObject) { out += "$key[$i] must be an object"; continue }
                        if (key == "update" && item.optString("id").isBlank()) out += "update[$i] needs an \"id\""
                        if (item.has("size") && sizeOf(item.optString("size")) == null) out += "$key[$i] has an unknown size \"${item.optString("size")}\""
                        if (key == "create" && !item.has("ui")) out += "create[$i] needs a \"ui\" node"
                        if (item.has("ui")) {
                            val ui = item.opt("ui")
                            if (ui !is JSONObject) out += "$key[$i].ui must be an object"
                            else node(ui, "$key[$i].ui", 0, intArrayOf(0), out)
                        }
                    }
                    "remind", "lookup" -> if (item !is JSONObject) out += "$key[$i] must be an object"
                    else -> if (item !is String) out += "$key[$i] must be text"
                }
            }
        }
        return out.distinct().take(8)
    }

    private fun node(n: JSONObject, where: String, depth: Int, count: IntArray, out: MutableList<String>) {
        if (depth > MAX_DEPTH) { out += "$where is nested too deeply (max $MAX_DEPTH)"; return }
        if (++count[0] > MAX_NODES) { out += "Card has more than $MAX_NODES parts"; return }
        val type = n.optString("type")
        if (type !in UiSchema.TYPES) { out += "$where has unknown type \"$type\""; return }
        val kids = n.opt("children")
        if (type in UiSchema.CONTAINERS) {
            if (kids !is JSONArray) { out += "$where ($type) needs a \"children\" list"; return }
            for (i in 0 until kids.length()) {
                val c = kids.opt(i)
                if (c is JSONObject) node(c, "$where.children[$i]", depth + 1, count, out) else out += "$where.children[$i] must be an object"
            }
        } else if (kids != null) out += "$where ($type) cannot have children"
        when (type) {
            "choice" -> {
                val opts = n.optJSONArray("options")
                if (n.optString("question").isBlank()) out += "$where (choice) needs a \"question\""
                if (opts == null || opts.length() !in 2..6) out += "$where (choice) needs 2 to 6 \"options\""
                else if (n.has("answer") && n.optInt("answer", -1) !in 0 until opts.length()) out += "$where (choice) \"answer\" is not an option index"
            }
            "list" -> if (n.optString("kind") == "schedule") {
                val days = n.optJSONArray("days")
                if (days == null || days.length() !in 1..7) out += "$where (schedule) needs 1 to 7 \"days\""
                else if ((0 until days.length()).all { (days.optJSONObject(it)?.optJSONArray("slots")?.length() ?: 0) == 0 })
                    out += "$where (schedule) has no classes: give every day its \"slots\", one {\"time\",\"subject\"} per class as printed"
                else for (d in 0 until days.length()) {
                    val day = days.optJSONObject(d)
                    val slots = day?.optJSONArray("slots")
                    if (day == null || day.optString("day").isBlank()) { out += "$where (schedule) days[$d] needs a \"day\""; continue }
                    if (slots == null || slots.length() > 16) { out += "$where (schedule) days[$d] needs up to 16 \"slots\""; continue }
                    for (i in 0 until slots.length()) {
                        val t = slots.optJSONObject(i)?.optString("time").orEmpty()
                        if (com.aicanvas.app.data.Schedule.range(t) == null) { out += "$where (schedule) days[$d].slots[$i] needs a \"time\" like \"8:00-8:50\""; break }
                    }
                }
            } else {
                val items = n.optJSONArray("items")
                if (items == null) out += "$where (list) needs \"items\""
                else if (items.length() > 20) out += "$where (list) has more than 20 items"
                else if ((0 until items.length()).any { i -> items.opt(i).let { it !is String && (it as? JSONObject)?.optString("title").isNullOrBlank() } })
                    out += "$where (list) every item needs a \"title\""
                else if (com.aicanvas.app.data.Schedule.looksLikeDays(items))
                    out += "$where (list) is a timetable drawn as day rows: use {\"type\":\"list\",\"kind\":\"schedule\",\"days\":[{\"day\":\"MON\",\"slots\":[{\"time\":\"8:00-8:50\",\"subject\":\"AI\"}]}]} with every class of every day as its own slot"
            }
            "progress", "ring" -> if (n.has("value") && ((n.opt("value") as? Number)?.toDouble() ?: -1.0) !in 0.0..1.0) out += "$where ($type) \"value\" must be between 0 and 1"
            "chart" -> {
                val v = n.optJSONArray("values")
                if (v == null || v.length() == 0) out += "$where (chart) needs \"values\""
                else for (i in 0 until v.length()) if (v.opt(i) !is Number) { out += "$where (chart) values must be numbers"; break }
            }
        }
    }
}
