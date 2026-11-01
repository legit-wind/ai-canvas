package com.aicanvas.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CanvasesTest {
    private fun card(text: String, canvas: String = Canvases.HOME_ID) =
        Element(ui = JSONObject().put("type", "text").put("text", text), canvas = canvas)

    @Test fun parseAlwaysHasHome() {
        assertEquals(Canvases.HOME_ID, Canvases.parse(null).first().id)
        assertEquals(Canvases.HOME_ID, Canvases.parse("garbage").first().id)
        val list = Canvases.parse(Canvases.serialize(listOf(CanvasInfo("w1", "Work"))))
        assertTrue(list.any { it.id == Canvases.HOME_ID } && list.any { it.name == "Work" })
    }

    @Test fun migrationMovesUnknownToHomeAndDropsNothing() {
        val cs = listOf(Canvases.HOME, CanvasInfo("w1", "Work"))
        val out = Canvases.migrate(listOf(card("a", ""), card("b", "gone"), card("c", "w1")), cs)
        assertEquals(3, out.size)
        assertEquals(listOf("home", "home", "w1"), out.map { it.canvas })
    }

    @Test fun oldJsonWithoutCanvasLoadsToHome() {
        val o = card("x").toJson().apply { remove("canvas") }
        assertEquals(Canvases.HOME_ID, Element.fromJson(o)!!.canvas)
    }

    @Test fun keywordClassifier() {
        assertEquals("Academic", Canvases.classify("Exam revision for chemistry"))
        assertEquals("Health", Canvases.classify("Drink water tracker"))
        assertEquals("Finance", Canvases.classify("Monthly expense budget"))
        assertEquals("News & Info", Canvases.classify("Weather today"))
        assertEquals("Personal", Canvases.classify("zzzz"))
    }

    @Test fun aiClassificationIsValidated() {
        val m = Canvases.parseClassification("Sure: {\"a\":\"health\",\"b\":\"Space\",\"z\":\"Work\"}", setOf("a", "b"), Canvases.CATEGORIES)
        assertEquals(mapOf("a" to "Health"), m)
        assertTrue(Canvases.parseClassification("no json", setOf("a"), Canvases.CATEGORIES).isEmpty())
    }

    @Test fun planAndEnsure() {
        val (list, c) = Canvases.ensure(listOf(Canvases.HOME), "Health") { "h1" }
        assertEquals("h1", c.id); assertEquals(2, list.size)
        val (again, c2) = Canvases.ensure(list, "health") { "x" }
        assertEquals(list, again); assertEquals("h1", c2.id)
        val plan = Canvases.plan(listOf(card("water", "h1"), card("exam")), list)
        assertEquals(listOf("Academic"), plan.map { it.category })
    }

    @Test fun staleness() {
        val now = 100 * Refresh.HOUR
        val weather = JSONObject().put("kind", "weather")
        val daily = JSONObject().put("kind", "daily")
        assertFalse(Refresh.isStale(null, 0, now))
        assertTrue(Refresh.isStale(daily, 0, now))
        assertFalse(Refresh.isStale(daily, now - 23 * Refresh.HOUR, now))
        assertTrue(Refresh.isStale(daily, now - 25 * Refresh.HOUR, now))
        assertTrue(Refresh.isStale(weather, now - 4 * Refresh.HOUR, now))
        assertFalse(Refresh.isStale(weather, now - 1 * Refresh.HOUR, now))
        assertTrue(Refresh.ago(now - 2 * Refresh.HOUR, now).contains("2"))
    }
}

class CanvasMergeTest {
    private fun card(id: String, canvas: String) = Element(id = id, ui = JSONObject().put("type", "text").put("text", id), canvas = canvas)
    private val cs = listOf(Canvases.HOME, CanvasInfo("w", "Work"), CanvasInfo("s", "Study"))
    private val els = listOf(card("1", "home"), card("2", "w"), card("3", "s"))

    @Test fun mergeTwoKeepsFirstAndRenames() {
        val m = Canvases.merge(els, cs, listOf("w", "s"), "School & Work")!!
        assertEquals(listOf("home", "w", "w"), m.elements.map { it.canvas })
        assertEquals(listOf("Home", "School & Work"), m.canvases.map { it.name })
        assertNull(Canvases.merge(els, cs, listOf("w"), "x"))
    }

    @Test fun mergeIncludingHomeKeepsHome() {
        val m = Canvases.merge(els, cs, listOf("s", "home"), "")!!
        assertEquals("home", m.target.id); assertEquals("Home", m.target.name)
        assertEquals(listOf("home", "w", "home"), m.elements.map { it.canvas })
    }

    @Test fun mergeAllIsOneCanvasAndKeepsEveryCard() {
        val m = Canvases.mergeAll(els, cs)
        assertEquals(listOf(Canvases.HOME_ID), m.canvases.map { it.id })
        assertEquals(3, m.elements.size); assertTrue(m.elements.all { it.canvas == "home" })
    }

    @Test fun aiRemovalScopes() {
        assertEquals(listOf("2"), Canvases.removalIds(els, cs, listOf("2", "nope"), emptyList(), "home"))
        assertEquals(listOf("3"), Canvases.removalIds(els, cs, emptyList(), listOf("study"), "home"))
        assertEquals(listOf("1"), Canvases.removalIds(els, cs, emptyList(), listOf("current"), "home"))
        assertEquals(listOf("1", "2", "3"), Canvases.removalIds(els, cs, emptyList(), listOf("all"), "home"))
        assertTrue(Canvases.removalIds(els, cs, emptyList(), listOf("Unknown"), "home").isEmpty())
        assertEquals("w", Canvases.resolve(cs, "work")?.id)
    }

    @Test fun parserReadsRemoveAll() {
        val r = com.aicanvas.app.ai.ReplyParser.parse("""{"reply":"ok","remove_all":["all"],"update":[{"id":"2","canvas":"Study"}]}""")
        assertEquals(listOf("all"), r.removeAll)
        assertEquals("Study", r.update.single().optString("canvas"))
    }
}
