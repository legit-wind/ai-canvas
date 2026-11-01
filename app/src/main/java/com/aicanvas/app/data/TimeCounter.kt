package com.aicanvas.app.data

import org.json.JSONObject

/**
 * A counter whose unit is a duration ("minutes", "min", "hours", "seconds"…) is shown as a stopwatch counting
 * toward its goal instead of − / + buttons. "value" stays in the counter's own unit (so daily history and streaks
 * keep working); while running, "runningSince" holds the epoch millis it was started, so it survives restarts.
 * Pure logic (no Compose), so it is unit-tested.
 */
object TimeCounter {
    /** Seconds in one [unit], or null when the unit isn't a duration. */
    fun unitSeconds(unit: String): Long? = when (unit.trim().lowercase().trimEnd('.')) {
        "s", "sec", "secs", "second", "seconds" -> 1L
        "m", "min", "mins", "minute", "minutes" -> 60L
        "h", "hr", "hrs", "hour", "hours" -> 3600L
        else -> null
    }

    fun isTime(n: JSONObject) = n.optString("type") == "counter" && unitSeconds(n.optString("unit")) != null

    fun isRunning(n: JSONObject) = n.optLong("runningSince", 0L) > 0L

    /** Total elapsed seconds at [nowMs]: the saved value plus the running stretch. */
    fun elapsedSeconds(n: JSONObject, nowMs: Long): Long {
        val per = unitSeconds(n.optString("unit")) ?: 1L
        val saved = Math.round(n.optDouble("value", 0.0) * per)
        val since = n.optLong("runningSince", 0L)
        return saved + if (since > 0L) maxOf(0L, (nowMs - since) / 1000) else 0L
    }

    fun goalSeconds(n: JSONObject): Long = Math.round(n.optDouble("goal", 0.0) * (unitSeconds(n.optString("unit")) ?: 1L))

    /** 0..1 toward the goal (0 when there is none). */
    fun progress(n: JSONObject, nowMs: Long): Float {
        val g = goalSeconds(n)
        return if (g <= 0) 0f else (elapsedSeconds(n, nowMs).toFloat() / g).coerceIn(0f, 1f)
    }

    /** 1625 s → "27:05"; an hour or more → "1:02:05". */
    fun clock(seconds: Long): String {
        val s = maxOf(0L, seconds)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    fun start(n: JSONObject, nowMs: Long) { if (!isRunning(n)) n.put("runningSince", nowMs) }

    /** Folds the running stretch into "value" (in the counter's unit) and stops. */
    fun pause(n: JSONObject, nowMs: Long) {
        if (!isRunning(n)) return
        val per = unitSeconds(n.optString("unit")) ?: 1L
        n.put("value", elapsedSeconds(n, nowMs).toDouble() / per)
        n.remove("runningSince")
    }

    fun reset(n: JSONObject) { n.put("value", 0); n.remove("runningSince") }
}
