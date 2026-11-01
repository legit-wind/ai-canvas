package com.aicanvas.app.ui

/**
 * Card buttons come from the AI, so they may only open a few safe kinds of link:
 * tel: (opens the dialer, never calls), https: and geo: (opened as browsable links), and alarm:HH:mm.
 */
object Links {
    private val ALARM = Regex("""alarm:\s*([01]?\d|2[0-3])\s*:\s*([0-5]?\d)\s*""")
    private val TEL = Regex("""tel:[0-9+*#()\-. ]{1,32}""")

    enum class Kind { TEL, WEB, GEO, ALARM }

    /** What kind of safe link [uri] is, or null when it must not be opened. */
    fun kindOf(uri: String): Kind? {
        val u = uri.trim()
        if (u.length > 2_000 || u.any { it.isISOControl() }) return null
        val lower = u.lowercase()
        return when {
            lower.startsWith("tel:") -> Kind.TEL.takeIf { TEL.matches(u) }
            lower.startsWith("https://") -> Kind.WEB.takeIf { u.length > "https://".length && ' ' !in u }
            lower.startsWith("geo:") -> Kind.GEO
            lower.startsWith("alarm:") -> Kind.ALARM.takeIf { ALARM.matches(u) }
            else -> null
        }
    }

    /** Hour and minute of a valid "alarm:HH:mm" link. */
    fun alarmTime(uri: String): Pair<Int, Int>? =
        ALARM.matchEntire(uri.trim())?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
}
