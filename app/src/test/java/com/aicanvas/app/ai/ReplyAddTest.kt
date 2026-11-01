package com.aicanvas.app.ai

import org.junit.Assert.*
import org.junit.Test

class ReplyAddTest {
    @Test fun parsesAddOps() {
        val r = ReplyParser.parse("""{"reply":"Logged.","add":{"expenses":[{"amount":120,"category":"Food","note":"Lunch"}],"tasks":["Buy milk"],"habits":[{"name":"Read"}],"countdowns":[{"title":"Exam","date":"2026-12-01"}],"bogus":[1]}}""")
        assertEquals(120.0, r.add["expenses"]!!.single().getDouble("amount"), 0.0)
        assertEquals("Buy milk", r.add["tasks"]!!.single().getString("title"))
        assertEquals("Read", r.add["habits"]!!.single().getString("name"))
        assertEquals("Exam", r.add["countdowns"]!!.single().getString("title"))
        assertFalse(r.add.containsKey("bogus"))
        assertTrue(r.create.isEmpty())
    }

    @Test fun noAddIsEmpty() { assertTrue(ReplyParser.parse("""{"reply":"hi"}""").add.isEmpty()) }
}
