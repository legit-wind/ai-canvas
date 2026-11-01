package com.aicanvas.app.data

import com.aicanvas.app.ai.ReplyParser
import com.aicanvas.app.ai.UiContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HandlingTest {
    // ---- long input ----
    @Test fun shortTextIsUntouched() = assertEquals("hi" to false, Limits.clip("hi"))
    @Test fun longTextIsCutAtAWord() {
        val (t, cut) = Limits.clip("word ".repeat(2000))
        assertTrue(cut); assertTrue(t.length <= Limits.MAX_MESSAGE); assertTrue(t.endsWith("word"))
    }
    @Test fun longTextWithoutSpacesStillCut() = assertEquals(Limits.MAX_MESSAGE, Limits.clip("x".repeat(9000)).first.length)

    // ---- photo too large ----
    @Test fun hugePhotoDecodesSmall() {
        assertEquals(1, Limits.sampleSize(1200, 900))
        assertEquals(8, Limits.sampleSize(12000, 9000))
        assertTrue(12000 / Limits.sampleSize(12000, 9000) >= Limits.PHOTO_EDGE)
    }
    @Test fun photoQualitiesGoDown() = assertEquals(Limits.PHOTO_QUALITIES.sortedDescending(), Limits.PHOTO_QUALITIES)

    // ---- rate limit / retry ----
    @Test fun retriesBusyAndServerErrorsOnly() {
        listOf(429, 408, 500, 503, 529).forEach { assertTrue("$it", Retry.worthRetry(it)) }
        listOf(400, 401, 402, 404).forEach { assertFalse("$it", Retry.worthRetry(it)) }
    }
    @Test fun backoffDoublesAndIsCapped() {
        assertEquals(2_000, Retry.delayMs(1, null)); assertEquals(4_000, Retry.delayMs(2, null)); assertEquals(8_000, Retry.delayMs(3, null))
        assertEquals(20_000, Retry.delayMs(9, null))
    }
    @Test fun honoursRetryAfter() {
        assertEquals(5_000, Retry.delayMs(1, "5")); assertEquals(20_000, Retry.delayMs(1, "600")); assertEquals(2_000, Retry.delayMs(1, "Wed, 21 Oct"))
    }

    // ---- model / http errors ----
    @Test fun errorsAreActionable() {
        assertTrue(Errors.http(401, null).contains("key"))
        assertTrue(Errors.http(429, null).contains("busy"))
        assertTrue(Errors.http(529, null).contains("overloaded"))
        assertTrue(Errors.http(400, "This model's maximum context length is 8192 tokens").contains("too long"))
        assertTrue(Errors.http(400, "model does not support image input").contains("photos"))
        assertTrue(Errors.http(418, "teapot").contains("418"))
    }

    // ---- missing key / offline ----
    @Test fun readiness() {
        assertTrue(Readiness.problem(false, true)!!.contains("key"))
        assertTrue(Readiness.problem(true, false)!!.contains("offline"))
        assertNull(Readiness.problem(true, true))
    }

    // ---- invalid JSON ----
    @Test fun brokenJsonGoesToRepair() {
        val raw = "{\"reply\":\"ok\",\"create\":[{\"ui\":{\"type\":\"text\" \"text\":\"x\"}}]}"
        assertNull(ReplyParser.jsonIn(raw))
        assertFalse(UiContract.problems(ReplyParser.jsonIn(raw)).isEmpty())
    }
    @Test fun trailingCommaAndFencesAreTolerated() = assertNotNull(ReplyParser.jsonIn("```json\n{\"reply\":\"ok\",}\n```"))
    @Test fun plainTextBecomesAReply() = assertEquals("Sorry, I can't do that.", ReplyParser.parse("Sorry, I can't do that.").text)

    // ---- reminders ----
    private val zone = ZoneId.systemDefault()
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun pastReminderIsCountedNotBooked() {
        val r = ReplyParser.parse("""{"reply":"ok","remind":[{"at":"2026-10-30T09:00","text":"x"},{"at":"2026-11-02T09:00","text":"y"}]}""", now = at("2026-10-31T10:00"))
        assertEquals(1, r.remind.size); assertEquals(1, r.pastReminders)
        assertNotNull(ReminderPlan.pastNote(1)); assertNull(ReminderPlan.pastNote(0))
    }
    @Test fun pastDailyReminderMovesToNextDay() {
        val r = ReplyParser.parse("""{"reply":"ok","remind":[{"at":"2026-10-31T09:00","text":"pill","repeat":"daily"}]}""", now = at("2026-10-31T10:00"))
        assertEquals(at("2026-11-01T09:00"), r.remind.single().at); assertEquals(0, r.pastReminders)
    }
    @Test fun afterRebootKeepsFutureShowsRecentMissedDropsOld() {
        val now = at("2026-10-31T10:00")
        val list = listOf(
            Reminder(id = "future", at = at("2026-10-31T12:00"), text = "a"),
            Reminder(id = "missed", at = at("2026-10-31T08:00"), text = "b"),
            Reminder(id = "old", at = at("2026-10-29T08:00"), text = "c"),
            Reminder(id = "daily", at = at("2026-10-30T09:00"), text = "d", repeat = "daily"),
        )
        val (keep, missed) = ReminderPlan.afterRestart(list, now) { it.copy().at + 86_400_000L * 2 }
        assertEquals(listOf("future", "daily"), keep.map { it.id })
        assertEquals(listOf("missed"), missed.map { it.id })
    }
    @Test fun zoneChangeKeepsClockTime() {
        val ist = ZoneId.of("Asia/Kolkata"); val dubai = ZoneId.of("Asia/Dubai")
        val nineIst = LocalDateTime.parse("2026-11-01T09:00").atZone(ist).toInstant().toEpochMilli()
        val shifted = shiftForZone(nineIst, ist, dubai)
        assertEquals(LocalDateTime.parse("2026-11-01T09:00"), java.time.Instant.ofEpochMilli(shifted).atZone(dubai).toLocalDateTime())
    }
}
