package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLogicTest {
    private fun quiz(picks: List<Int?>) = JSONObject("""{"type":"column","children":[
        {"type":"choice","question":"a","options":["x","y"],"answer":0},
        {"type":"choice","question":"b","options":["x","y"],"answer":1},
        {"type":"text","text":"Score","score":true}]}""").also { ui ->
        picks.forEachIndexed { i, p -> if (p != null) ui.getJSONArray("children").getJSONObject(i).put("picked", p) }
    }
    private fun scoreText(ui: JSONObject) = ui.getJSONArray("children").getJSONObject(2).getString("text")

    @Test fun quizScoreUpdatesLocally() {
        val ui = quiz(listOf(0, null)); assertTrue(CardLogic.refresh(ui, 0)); assertEquals("Score 1 / 2 · 1 left", scoreText(ui))
        val all = quiz(listOf(0, 1)); CardLogic.refresh(all, 0); assertTrue(scoreText(all).contains("perfect"))
        val wrong = quiz(listOf(1, 1)); CardLogic.refresh(wrong, 0); assertEquals("Score 1 / 2 · done", scoreText(wrong))
    }

    @Test fun focusSessionCountedOnce() {
        val t = JSONObject("""{"type":"timer","minutes":25,"log":true,"endsAt":1000}""")
        assertTrue(CardLogic.refresh(t, 2000)); assertFalse(CardLogic.refresh(t, 3000))
        assertEquals(1, t.getInt("sessions"))
        t.put("endsAt", 9000); CardLogic.refresh(t, 4000); t.put("endsAt", 5000); CardLogic.refresh(t, 6000)
        assertEquals(2, t.getInt("sessions"))
    }

    @Test fun newDayUnticksDailyChecksAndLogsThem() {
        val ui = JSONObject("""{"type":"column","children":[{"type":"check","label":"9 AM","daily":true,"checked":true},{"type":"check","label":"buy","checked":true}]}""")
        assertTrue(CardLogic.rollOver(ui, "Sat"))
        val dose = ui.getJSONArray("children").getJSONObject(0)
        assertFalse(dose.getBoolean("checked"))
        assertEquals(1 to 1, CardLogic.takenDays(dose.getJSONArray("history")))
        assertTrue(ui.getJSONArray("children").getJSONObject(1).getBoolean("checked"))
    }

    @Test fun historyKeepsSevenDays() {
        val c = JSONObject("""{"type":"counter","daily":true,"value":3}""")
        repeat(10) { CardLogic.rollOver(c, "D$it") }
        assertEquals(7, c.getJSONArray("history").length())
    }

    @Test fun streakCountsDaysAtGoal() {
        val h = JSONArray("""[{"value":8},{"value":2},{"value":8},{"value":9}]""")
        assertEquals(2, CardLogic.streak(h, 8.0, 3.0)); assertEquals(3, CardLogic.streak(h, 8.0, 8.0)); assertEquals(0, CardLogic.streak(h, 0.0, 9.0))
    }

    @Test fun redesignKeepsPicksSessionsAndHistory() {
        val old = JSONObject("""{"type":"column","children":[{"type":"choice","question":"Q","options":["a","b"],"picked":1},{"type":"timer","label":"Focus","log":true,"sessions":3},{"type":"check","label":"Pill","daily":true,"history":[{"done":true}]}]}""")
        val new = JSONObject("""{"type":"column","children":[{"type":"choice","question":"Q","options":["a","b"]},{"type":"timer","label":"Focus","log":true},{"type":"check","label":"Pill","daily":true}]}""")
        UiSchema.keepUserState(old, new)
        val k = new.getJSONArray("children")
        assertEquals(1, k.getJSONObject(0).getInt("picked")); assertEquals(3, k.getJSONObject(1).getInt("sessions")); assertEquals(1, k.getJSONObject(2).getJSONArray("history").length())
    }
}
