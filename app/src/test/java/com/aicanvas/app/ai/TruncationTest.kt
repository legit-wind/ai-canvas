package com.aicanvas.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TruncationTest {
    private val full = """{"reply":"Here is your timetable.","create":[
      {"size":"half","tone":"blue","ui":{"type":"stack","children":[{"type":"text","text":"Today · Mon","style":"title"},{"type":"list","items":[{"title":"AI","meta":"8:00"},{"title":"CS","meta":"9:00"}]}]}},
      {"size":"full","tone":"blue","ui":{"type":"stack","children":[{"type":"text","text":"Week","style":"title"},{"type":"list","items":[{"title":"MON","detail":"AI · CS · CE"},{"title":"TUE","detail":"VLSI · DSP"}]}]}}]}"""

    private fun body(content: String, finish: String) =
        JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("finish_reason", finish).put("message", JSONObject().put("content", content)))).toString()

    @Test fun lengthStopIsReportedWithThePartialText() {
        try { ChatCompletion.content(body(full.take(300), "length")); fail() } catch (t: Truncated) { assertEquals(full.take(300).trim(), t.partial) }
    }
    @Test fun unclosedThinkIsDropped() {
        try { ChatCompletion.content(body("<think>long reasoning", "length")); fail() } catch (t: Truncated) { assertEquals("", t.partial) }
    }

    @Test fun cutMidSecondCardKeepsTheFirstCard() {
        val cut = full.substring(0, full.indexOf("TUE"))
        val o = Salvage.close(cut)!!
        assertTrue(UiContract.problems(o).isEmpty())
        assertEquals("Today · Mon", o.getJSONArray("create").getJSONObject(0).getJSONObject("ui").getJSONArray("children").getJSONObject(0).getString("text"))
        val r = ReplyParser.parse(Ai.keepFinishedPart(cut))
        assertTrue(r.create.isNotEmpty()); assertTrue(r.text.contains("kept the part"))
    }
    @Test fun completeJsonIsUnchanged() = assertEquals(JSONObject(full).toString(), Salvage.close(full).toString())
    @Test fun cutInsideAStringWithBracesIsSafe() = assertTrue(Salvage.close("""{"reply":"a } ] tricky","create":[{"ui":{"type":"text","text":"x"}},{"ui":{"type":"te""") != null)
    @Test fun nothingFinishedGivesAFriendlyError() {
        assertNull(Salvage.close("""{"rep"""))
        try { Ai.keepFinishedPart("<think>"); fail() } catch (e: AiException) { assertTrue(e.message!!.contains("cut off")) }
    }
    @Test fun retryAsksForCompactTimetable() = assertTrue(Ai.SHORTER.contains("timetable"))
}
