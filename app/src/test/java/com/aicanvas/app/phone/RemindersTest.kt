package com.aicanvas.app.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RemindersTest {
    private val zone = ZoneId.systemDefault()
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun hour(t: Long) = Instant.ofEpochMilli(t).atZone(zone).hour

    @Test fun dailyAndWeeklyMoveToTheNextFutureTime() {
        assertEquals(at(31, 9), Reminders.nextAfter(at(28, 9), "daily", at(30, 12)))
        assertEquals(at(31, 9), Reminders.nextAfter(at(24, 9), "weekly", at(30, 12)))
    }

    @Test fun hourlySkipsTheNight() {
        val next = Reminders.nextAfter(at(30, 20), "hours:2", at(30, 21, 30))
        assertTrue(hour(next) in 8..21)
        assertTrue(next > at(30, 21, 30))
    }
}
