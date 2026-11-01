package com.aicanvas.app.data

import com.aicanvas.app.ai.ReplyParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CompactCardsTest {
    private val now = LocalDateTime.of(2026, 11, 2, 9, 10) // a Monday

    @Test fun foldsChecksIntoCount() {
        val ui = JSONObject("""{"type":"stack","children":[{"type":"text","style":"title","text":"🛒 Groceries"},
            {"type":"check","label":"Milk","checked":true},{"type":"check","label":"Eggs"},{"type":"check","label":"Rice"},{"type":"check","label":"Oil"}]}""")
        assertFalse(CardSummary.isSmall(ui))
        val f = CardSummary.fold(ui, now)
        assertEquals("🛒 Groceries", f.title)
        assertEquals(listOf("✓ 1 of 4 done"), f.lines)
    }

    @Test fun foldsCountdownAndList() {
        val ui = JSONObject("""{"type":"stack","children":[{"type":"text","style":"title","text":"Exam"},
            {"type":"timer","to":"2026-11-03T21:10"},{"type":"list","items":[{"title":"Maths"},{"title":"Physics"},{"title":"Chem"}]}]}""")
        assertEquals(listOf("⏳ 1d 12h left", "Maths · +2 more"), CardSummary.fold(ui, now).lines)
    }

    @Test fun foldsTimetableToNextClass() {
        val ui = JSONObject("""{"type":"list","kind":"schedule","title":"📅 Timetable","days":[{"day":"MON","slots":[{"time":"9:00-9:50","subject":"AI"},{"time":"10:00-10:50","subject":"DSP"}]}]}""")
        val f = CardSummary.fold(ui, now)
        assertEquals("📅 Timetable", f.title)
        assertEquals(listOf("Now: AI at 9:00"), f.lines)
    }

    @Test fun smallCardsStayWhole() {
        assertTrue(CardSummary.isSmall(JSONObject("""{"type":"stack","children":[{"type":"text","text":"💧 Water"},{"type":"counter","value":3,"goal":8}]}""")))
    }

    @Test fun expandedIsSaved() {
        val e = Element(ui = JSONObject().put("type", "text"), expanded = true)
        assertTrue(Element.fromJson(e.toJson()).expanded)
        assertFalse(Element.fromJson(JSONObject().put("id", "x")).expanded)
    }

    @Test fun timetableRowsBecomeSchedule() {
        val raw = """{"create":[{"ui":{"type":"stack","children":[{"type":"list","items":[
            {"title":"MON","meta":"9:00-4:00","classes":[{"start":"9:00","end":"9:50","name":"AI"}]},
            {"title":"TUE","slots":[{"time":"9:00-9:50","subject":"CS"}]}]}]}}]}"""
        val list = ReplyParser.jsonIn(raw)!!.getJSONArray("create").getJSONObject(0).getJSONObject("ui").getJSONArray("children").getJSONObject(0)
        assertEquals("schedule", list.getString("kind"))
        val days = Schedule.parse(list)
        assertEquals(2, days.size)
        assertEquals("AI", days[0].slots[0].subject)
        assertEquals(9 * 60, days[0].slots[0].start)
    }

    @Test fun timetableTypeIsAdopted() {
        val n = JSONObject("""{"type":"timetable","days":[{"name":"WED","periods":[{"time":"8:00-8:50","title":"VLSI"}]}]}""")
        Schedule.adopt(n)
        assertEquals("list", n.getString("type"))
        assertEquals("VLSI", Schedule.parse(n)[0].slots[0].subject)
    }

    @Test fun ordinaryListsUntouched() {
        val n = JSONObject("""{"type":"list","items":[{"title":"Milk"}]}""")
        Schedule.adopt(n)
        assertFalse(n.has("kind"))
    }

    @Test fun photoLimits() {
        assertEquals(5, Limits.roomFor(0, 9))
        assertEquals(2, Limits.roomFor(3, 4))
        assertEquals(0, Limits.roomFor(5, 1))
        assertEquals(Limits.MAX_PHOTO_BYTES, Limits.photoBudget(1))
        assertTrue(Limits.photoBudget(5) * 5 <= Limits.MAX_TOTAL_PHOTO_BYTES)
    }
}

class TileTest {
    private fun big(expanded: Boolean = false) = Element(ui = JSONObject("""{"type":"list","items":[{"title":"A"},{"title":"B"}]}"""), expanded = expanded)

    @Test fun emojiIsSplitFromTitle() {
        assertEquals("📅" to "Final exams", CardSummary.splitEmoji("📅 Final exams"))
        assertEquals("" to "Week timetable", CardSummary.splitEmoji("Week timetable"))
    }

    @Test fun foldedCardsSitTwoToARow() {
        val rows = com.aicanvas.app.ui.packRowsForTest(listOf(big(), big(), big()))
        // A lone folded tile fills its row instead of leaving an empty half.
        assertEquals(listOf(listOf(3, 3), listOf(6)), rows)
    }

    @Test fun openedCardTakesFullWidth() {
        assertEquals(listOf(listOf(6), listOf(6)), com.aicanvas.app.ui.packRowsForTest(listOf(big(expanded = true), big())))
        // The other tile of an expanded pair fills the row width.
        assertEquals(listOf(listOf(6), listOf(6), listOf(3, 3)), com.aicanvas.app.ui.packRowsForTest(listOf(big(), big(expanded = true), big(), big())).let { listOf(it[0], it[1]) + it.drop(2) })
    }
}
