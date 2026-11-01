package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Card behaviour that runs on the phone without the AI: quiz scores, focus-session logs, daily ticks with a
 * 7-day log, and habit streaks. Pure JSON in, JSON out, so it is unit-tested.
 */
object CardLogic {
    const val HISTORY_DAYS = 7

    private fun walk(n: JSONObject, block: (JSONObject) -> Unit) {
        block(n)
        val kids = n.optJSONArray("children") ?: return
        for (i in 0 until kids.length()) kids.optJSONObject(i)?.let { walk(it, block) }
    }

    /** Correct answers, answered questions and total quiz questions (choices that have an "answer"). */
    fun quizScore(ui: JSONObject): Triple<Int, Int, Int> {
        var right = 0; var answered = 0; var total = 0
        walk(ui) { n ->
            if (n.optString("type") == "choice" && n.has("answer")) {
                total++
                if (n.has("picked")) {
                    answered++
                    if (n.optInt("picked", -1) == n.optInt("answer", -2)) right++
                }
            }
        }
        return Triple(right, answered, total)
    }

    /**
     * Brings the card's derived parts up to date after the user did something. Returns true if anything changed.
     * - a text with "score": true shows the quiz score;
     * - a timer with "log": true counts each finished focus session once ("sessions").
     */
    fun refresh(ui: JSONObject, now: Long): Boolean {
        var changed = false
        val (right, answered, total) = quizScore(ui)
        walk(ui) { n ->
            when (n.optString("type")) {
                "text" -> if (n.optBoolean("score") && total > 0) {
                    val t = "Score $right / $total" + when {
                        answered < total -> " · ${total - answered} left"
                        right == total -> " · perfect! 🎉"
                        else -> " · done"
                    }
                    if (n.optString("text") != t) { n.put("text", t); changed = true }
                }
                "timer" -> if (n.optBoolean("log")) {
                    val endsAt = n.optLong("endsAt")
                    if (endsAt in 1..now && !n.optBoolean("counted")) {
                        n.put("sessions", n.optInt("sessions") + 1).put("counted", true); changed = true
                    } else if (endsAt > now && n.optBoolean("counted")) {
                        n.put("counted", false); changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun push(history: JSONArray?, entry: JSONObject): JSONArray {
        val h = history ?: JSONArray()
        h.put(entry)
        while (h.length() > HISTORY_DAYS) h.remove(0)
        return h
    }

    /**
     * A new day started; [day] is the short name of the day that just ended. Daily counters go to 0, daily
     * checks are unticked and focus sessions restart, each keeping a 7-day "history". Returns true if changed.
     */
    fun rollOver(ui: JSONObject, day: String): Boolean {
        var changed = false
        walk(ui) { n ->
            when (n.optString("type")) {
                "counter" -> if (n.optBoolean("daily")) {
                    n.put("history", push(n.optJSONArray("history"), JSONObject().put("day", day).put("value", n.optDouble("value", 0.0))))
                    n.put("value", 0); changed = true
                }
                "check" -> if (n.optBoolean("daily")) {
                    n.put("history", push(n.optJSONArray("history"), JSONObject().put("day", day).put("done", n.optBoolean("checked"))))
                    n.put("checked", false); changed = true
                }
                "timer" -> if (n.optBoolean("log")) {
                    n.put("history", push(n.optJSONArray("history"), JSONObject().put("day", day).put("value", n.optInt("sessions"))))
                    n.put("sessions", 0); changed = true
                }
            }
        }
        return changed
    }

    /** Days in a row (ending yesterday, plus today if already met) that a daily counter reached [goal]. */
    fun streak(history: JSONArray?, goal: Double, today: Double): Int {
        if (goal <= 0) return 0
        var n = 0
        if (history != null) for (i in history.length() - 1 downTo 0) {
            if ((history.optJSONObject(i)?.optDouble("value", 0.0) ?: 0.0) >= goal) n++ else break
        }
        return n + if (today >= goal) 1 else 0
    }

    /** For a daily check: days ticked and days logged in its history, e.g. medicine taken 6 of 7 days. */
    fun takenDays(history: JSONArray?): Pair<Int, Int> {
        if (history == null) return 0 to 0
        val days = (0 until history.length()).mapNotNull { history.optJSONObject(it) }
        return days.count { it.optBoolean("done") } to days.size
    }
}
