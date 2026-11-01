package com.aicanvas.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HabitsTest {
    private val sat = LocalDate.of(2026, 10, 31) // Saturday
    private fun h(vararg done: String, days: Set<Int> = (1..7).toSet()) = Habit(name = "x", days = days, done = done.toSet())

    @Test fun streakCountsBackAndTodayPendingDoesNotBreak() {
        assertEquals(2, Habits.streak(h("2026-10-29", "2026-10-30"), sat))
        assertEquals(3, Habits.streak(h("2026-10-29", "2026-10-30", "2026-10-31"), sat))
        assertEquals(0, Habits.streak(h("2026-10-28"), sat))
    }

    @Test fun unscheduledDaysAreSkipped() {
        // Weekdays only: Fri 30 and Thu 29 done, Sat today not due.
        assertEquals(2, Habits.streak(h("2026-10-29", "2026-10-30", days = (1..5).toSet()), sat))
        // Mon 26 … Fri 30 done, weekend in between previous week is skipped.
        assertEquals(6, Habits.streak(h("2026-10-23", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", days = (1..5).toSet()), sat))
    }

    @Test fun bestAndRate() {
        val x = h("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-10")
        assertEquals(3, Habits.best(x))
        assertEquals(3 to 30, Habits.rate(x, sat))
        assertNull(Habits.rate(h(days = setOf(1)), LocalDate.of(2026, 10, 31), days = 1))
    }

    @Test fun toggleAndJson() {
        val x = Habits.toggle(h(), sat)
        assertTrue("2026-10-31" in x.done)
        assertTrue(Habits.toggle(x, sat).done.isEmpty())
        assertEquals(x, Habit.fromJson(x.toJson()))
        assertNull(Habit.fromJson(org.json.JSONObject("{\"name\":\"\"}")))
    }
}
