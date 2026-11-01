package com.aicanvas.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEditsTest {
    private fun list() = JSONObject("""{"type":"column","children":[{"type":"check","label":"Milk","checked":true},{"type":"input","add":"check"}]}""")

    @Test fun addsAboveInputAndKeepsTicks() {
        val ui = list()
        LocalEdits.addCheck(ui, 1, " Eggs ")
        val kids = ui.getJSONArray("children")
        assertEquals(3, kids.length())
        assertTrue(kids.getJSONObject(0).getBoolean("checked"))
        assertEquals("Eggs", kids.getJSONObject(1).getString("label"))
        assertEquals("input", kids.getJSONObject(2).getString("type"))
    }
    @Test fun ignoresBlank() { val ui = list(); LocalEdits.addCheck(ui, 1, "  "); assertEquals(2, ui.getJSONArray("children").length()) }
    @Test fun ignoresWrongIndex() { val ui = list(); LocalEdits.addCheck(ui, 0, "x"); assertEquals(2, ui.getJSONArray("children").length()) }
}
