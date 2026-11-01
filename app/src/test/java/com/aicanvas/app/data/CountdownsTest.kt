package com.aicanvas.app.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class CountdownsTest {
    private val today = LocalDate.of(2026, 10, 31)
    @Test fun labels() {
        assertEquals("Today", Countdowns.label(0)); assertEquals("Tomorrow", Countdowns.label(1))
        assertEquals("in 30 days", Countdowns.label(30)); assertEquals("3 days ago", Countdowns.label(-3))
        assertEquals(1L, Countdowns.daysLeft(Countdown(title = "x", date = "2026-11-01"), today))
    }
    @Test fun sortAndJson() {
        val l = listOf(Countdown(title = "old", date = "2026-10-01"), Countdown(title = "far", date = "2027-03-01"),
            Countdown(title = "soon", date = "2026-11-05"), Countdown(title = "older", date = "2026-01-01"))
        assertEquals(listOf("soon", "far", "old", "older"), Countdowns.sorted(l, today).map { it.title })
        assertEquals(l[0], Countdown.fromJson(l[0].toJson()))
        assertNull(Countdown.fromJson(JSONObject("{\"title\":\"x\",\"date\":\"nope\"}")))
    }
}
