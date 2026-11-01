package com.aicanvas.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeCounterTest {
    private fun c(unit: String, value: Double = 0.0, goal: Double = 30.0) =
        JSONObject().put("type", "counter").put("unit", unit).put("value", value).put("goal", goal)

    @Test fun detectsTimeUnits() {
        listOf("minutes", "min", "Mins", "hours", "hr", "seconds", "sec", " Minute ").forEach { assertTrue(it, TimeCounter.isTime(c(it))) }
        listOf("glasses", "pages", "", "steps", "km").forEach { assertFalse(it, TimeCounter.isTime(c(it))) }
        assertNull(TimeCounter.unitSeconds("glasses"))
        assertEquals(3600L, TimeCounter.unitSeconds("hours"))
    }

    @Test fun convertsAndFormats() {
        assertEquals("27:00", TimeCounter.clock(TimeCounter.elapsedSeconds(c("minutes", 27.0), 0)))
        assertEquals("1:02:05", TimeCounter.clock(3725))
        assertEquals(1800L, TimeCounter.goalSeconds(c("minutes")))
        assertEquals(0.9f, TimeCounter.progress(c("minutes", 27.0), 0), 0.001f)
    }

    @Test fun startPausePersistsViaTimestamp() {
        val n = c("minutes", 1.0)
        TimeCounter.start(n, 1_000_000L)
        assertTrue(TimeCounter.isRunning(n))
        // Survives a restart: elapsed is computed from the stored start time.
        val reloaded = JSONObject(n.toString())
        assertEquals(60L + 90L, TimeCounter.elapsedSeconds(reloaded, 1_090_000L))
        TimeCounter.pause(reloaded, 1_090_000L)
        assertFalse(TimeCounter.isRunning(reloaded))
        assertEquals(2.5, reloaded.getDouble("value"), 1e-9)
        TimeCounter.reset(reloaded)
        assertEquals(0L, TimeCounter.elapsedSeconds(reloaded, 5_000_000L))
    }
}
