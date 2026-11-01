package com.aicanvas.app.data

import com.aicanvas.app.ai.ReplyParser
import com.aicanvas.app.ai.UiContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone showed MON..SAT rows (summary + time range) that could not open: such answers must be repaired. */
class TimetableRepairTest {
    private val rowsReply = """{"reply":"Here is your week","create":[{"size":"full","tone":"blue","ui":{"type":"stack","children":[
        {"type":"text","style":"title","text":"Week timetable"},
        {"type":"stack","direction":"row","spread":true,"children":[{"type":"text","style":"caption","text":"Sat · No classes today"},{"type":"timer","to":"2026-11-02T08:00"}]},
        {"type":"list","items":[
          {"title":"MON","detail":"AI · CS · CE · VLSI · DSP LAB","meta":"8:00 AM - 5:50 PM"},
          {"title":"TUE","detail":"CE · VLSI · DSP · CE LAB · AI","meta":"9:00 AM - 5:50 PM"},
          {"title":"WED","detail":"VLSI · DSP · HM · CS","meta":"9:00 AM - 3:50 PM"},
          {"title":"SAT","detail":"No classes","meta":"—"}]}]}}]}"""

    private val fixedReply = """{"reply":"Fixed","create":[{"size":"full","tone":"blue","ui":{"type":"stack","children":[
        {"type":"list","kind":"schedule","title":"Week timetable","days":[
          {"day":"MON","slots":[{"time":"8:00-8:50","subject":"AI"},{"time":"2:00-5:50","subject":"DSP LAB"}]},
          {"day":"TUE","slots":[{"time":"9:00-9:50","subject":"CE"}]}]}]}}]}"""

    @Test fun dayRowsWithoutSlotsAreRejected() {
        val problems = UiContract.problems(ReplyParser.jsonIn(rowsReply))
        assertTrue(problems.toString(), problems.any { "kind" in it && "schedule" in it && "slot" in it })
    }

    @Test fun repairedAnswerPassesAndKeepsSlots() {
        val json = ReplyParser.jsonIn(fixedReply)
        assertEquals(emptyList<String>(), UiContract.problems(json))
        val list = json!!.getJSONArray("create").getJSONObject(0).getJSONObject("ui").getJSONArray("children").getJSONObject(0)
        val days = Schedule.parse(list)
        assertEquals(listOf(2, 1), days.map { it.slots.size })
        assertEquals("DSP LAB", days[0].slots[1].subject)
    }

    @Test fun ordinaryWeekPlanIsFine() {
        val plan = """{"create":[{"ui":{"type":"list","items":[{"title":"Mon","detail":"Oats"},{"title":"Tue","detail":"Poha"},{"title":"Wed","detail":"Idli"}]}}]}"""
        assertEquals(emptyList<String>(), UiContract.problems(ReplyParser.jsonIn(plan)))
    }
}
