package com.aicanvas.app.data

import com.aicanvas.app.ai.UiContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleTest {
    private val node = JSONObject(javaClass.classLoader!!.getResource("timetable.json")!!.readText())
    private val days = Schedule.parse(node)

    @Test fun readsTheWeek() {
        assertEquals(listOf("MON", "TUE", "WED", "THU", "FRI"), days.map { it.name })
        assertEquals("AI · CS · CE · VLSI · Lunch · DSP LAB", Schedule.summary(days[0]))
    }
    @Test fun afternoonTimesAndLabRange() {
        val lab = days[0].slots.last()
        assertEquals(14 * 60 to 15 * 60 + 50, lab.start to lab.end); assertEquals("2:00-3:50", lab.time)
    }
    @Test fun gapsShowAsFreeAndLabSlotsMerge() {
        val mon = Schedule.parse(JSONObject("""{"days":[{"day":"Monday","slots":[
            {"time":"11:00-11:50","subject":"VLSI"},{"time":"12:00-12:50","subject":""},{"time":"2:00-2:50","subject":"DSP LAB"},
            {"time":"3:00-3:50","subject":"DSP LAB"},{"time":"4:00-4:50","subject":"free"},{"time":"5:00-5:50","subject":"Free"}]}]}""")).single()
        assertEquals("MON", mon.name)
        assertEquals(listOf("VLSI", "", "DSP LAB"), mon.slots.map { it.subject }) // trailing free dropped
        assertEquals("2:00-3:50", mon.slots.last().time)
    }
    @Test fun todayAndNowNext() {
        assertEquals(0, Schedule.todayIndex(days, DayOfWeek.MONDAY)); assertEquals(-1, Schedule.todayIndex(days, DayOfWeek.SATURDAY))
        assertEquals(1 to "Now", Schedule.current(days[0], LocalTime.of(9, 20)))
        assertEquals(1 to "Next", Schedule.current(days[0], LocalTime.of(8, 55)))
        assertEquals(5 to "Now", Schedule.current(days[0], LocalTime.of(15, 0)))
        assertNull(Schedule.current(days[0], LocalTime.of(17, 0)))
    }
    @Test fun eachSubjectHasItsOwnToneAndLabsMatch() {
        val t = Schedule.tones(days)
        assertEquals(setOf("AI", "CE", "CS", "DSP", "HM", "VLSI"), t.keys)
        assertEquals(6, t.values.toSet().size)
        assertEquals(t["DSP"], t[Schedule.base("DSP LAB")])
    }
    @Test fun amPmAndDashes() {
        assertEquals(13 * 60 to 13 * 60 + 50, Schedule.range("1:00–1:50"))
        assertEquals(9 * 60 to 10 * 60, Schedule.range("9 am - 10 am"))
        assertNull(Schedule.range("soon"))
    }
    @Test fun validatorAcceptsTheTimetable() {
        val reply = JSONObject().put("create", org.json.JSONArray().put(JSONObject().put("ui", node)))
        assertTrue(UiContract.problems(reply).joinToString(), UiContract.problems(reply).isEmpty())
    }
    @Test fun oldListsStillWork() = assertTrue(UiContract.problems(JSONObject("""{"create":[{"ui":{"type":"list","items":[{"title":"MON","detail":"AI · CS"}]}}]}""")).isEmpty())
}
