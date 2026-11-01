package com.aicanvas.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRankTest {
    private fun m(text: String, t: Long) = Memory(text = text, createdAt = t)

    @Test fun smallMemoryIsSentWhole() {
        val all = listOf(m("Lives in Pune", 1), m("Studies CSE", 2))
        assertEquals(all, MemoryRank.pick(all, "weather"))
    }
    @Test fun relevantFactsWinOverNewOnes() {
        val all = (1..30).map { m("Random fact $it", it.toLong()) } + m("Monthly budget is 5000 rupees", 0)
        val picked = MemoryRank.pick(all, "make a budget tracker", limit = 5)
        assertEquals("Monthly budget is 5000 rupees", picked.first().text)
        assertEquals(5, picked.size)
    }
    @Test fun newestBreaksTies() {
        val all = (1..20).map { m("fact $it", it.toLong()) }
        assertEquals("fact 20", MemoryRank.pick(all, "zzz", limit = 3).first().text)
    }
    @Test fun duplicatesIgnoreCaseAndPunctuation() {
        val all = listOf(m("The user lives in Pune.", 1))
        assertTrue(MemoryRank.isDuplicate("user lives in pune", all))
        assertFalse(MemoryRank.isDuplicate("User studies in Pune", all))
    }
}
