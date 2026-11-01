package com.aicanvas.app.ui

import com.aicanvas.app.data.Element
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private fun card(size: String, ui: String = """{"type":"text","text":"hi"}""") = Element(size = size, ui = JSONObject(ui))

    @Test fun everyRowIsFullWidth() {
        val rows = packRows(listOf(card("third"), card("half"), card("full"), card("third"), card("twothirds"), card("half")))
        rows.forEach { row -> assertEquals(6, row.sumOf { it.second }) }
        assertEquals(6, rows.flatten().size)
    }

    @Test fun contentNeedsCanWidenACard() {
        assertEquals(4, spanOf(card("third", """{"type":"timer","minutes":5}""")))
        assertEquals(6, minSpan(JSONObject("""{"type":"column","children":[{"type":"check"},{"type":"check"},{"type":"check"},{"type":"check"}]}""")))
    }

    @Test fun emptyListHasNoRows() {
        assertTrue(packRows(emptyList()).isEmpty())
    }
}
