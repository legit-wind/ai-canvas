package com.aicanvas.app.ui

import com.aicanvas.app.data.Element
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandInPlaceTest {
    // A list makes a card "big", so it folds into a tile until opened.
    private fun big(id: String, expanded: Boolean = false) = Element(
        id = id, expanded = expanded,
        ui = JSONObject().put("type", "stack").put("children", JSONArray().put(JSONObject().put("type", "list").put("items", JSONArray().put("a")))),
    )

    private fun order(list: List<Element>) = packRows(list).map { row -> row.map { it.first.id } }

    @Test fun openedTileStaysInPlace() {
        val tiles = listOf(big("a"), big("b"), big("c"), big("d"), big("e"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), order(tiles))
        // Open "b": "a" keeps its row, "b" takes the next full row, then c, d, e flow after it in order.
        val opened = tiles.map { if (it.id == "b") it.copy(expanded = true) else it }
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c", "d"), listOf("e")), order(opened))
        // Open "c" (first in its row): it stays right there.
        val c = tiles.map { if (it.id == "c") it.copy(expanded = true) else it }
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d", "e")), order(c))
    }
}
