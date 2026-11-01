package com.aicanvas.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReplyParserTest {
    private val now = LocalDateTime.of(2026, 10, 30, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun findsJsonInsideFencesAndTrailingCommas() {
        assertNotNull(ReplyParser.jsonIn("Sure!\n```json\n{\"reply\":\"ok\",\"remove\":[\"a\",],}\n```"))
        assertNull(ReplyParser.jsonIn("no json here"))
    }

    @Test fun plainTextBecomesTextReply() {
        val r = ReplyParser.parse("Hello there", now)
        assertEquals("Hello there", r.text)
        assertTrue(r.create.isEmpty())
    }

    @Test fun parsesCreatesAndDropsBadOnes() {
        val r = ReplyParser.parse(
            """{"reply":"Added","create":[
                {"size":"half","tone":"blue","importance":250,"ui":{"type":"text","text":"Hi"}},
                {"ui":{"type":"iframe"}},
                {"ui":"nope"}],
              "update":[{"id":"x","ui":{"type":"bogus"}},{"id":"y","size":"full"}],
              "remove":["z"],"remember":["likes tea",""]}""",
            now,
        )
        assertEquals("Added", r.text)
        assertEquals(1, r.create.size)
        assertEquals("half", r.create[0].size)
        assertEquals(100.0, r.create[0].importance, 0.0)
        assertEquals(listOf("y"), r.update.map { it.getString("id") })
        assertEquals(listOf("z"), r.remove)
        assertEquals(listOf("likes tea"), r.remember)
    }

    @Test fun remindersInThePastAreDroppedAndRepeatsMoveForward() {
        val r = ReplyParser.parse(
            """{"remind":[
                {"at":"2026-10-30T11:00","text":"past"},
                {"at":"2026-10-30T13:00","text":"later"},
                {"at":"2026-10-29T09:00","text":"daily","repeat":"daily"},
                {"at":"garbage","text":"bad"}]}""",
            now,
        )
        assertEquals(listOf("later", "daily"), r.remind.map { it.text })
        assertTrue(r.remind.all { it.at > now })
        assertEquals("daily", r.remind[1].repeat)
    }
}
