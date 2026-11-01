package com.aicanvas.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PrimitivesTest {
    private fun s(json: String) = UiSchema.sanitize(JSONObject(json))!!

    @Test fun twelvePrimitives() = assertEquals(12, UiSchema.PRIMITIVES.size)
    @Test fun stackBecomesColumnOrRow() {
        val ui = s("""{"type":"stack","children":[{"type":"stack","direction":"row","children":[]}]}""")
        assertEquals("column", ui.getString("type"))
        assertEquals("row", ui.getJSONArray("children").getJSONObject(0).getString("type"))
    }
    @Test fun ringProgress() = assertEquals("ring", s("""{"type":"progress","shape":"ring","value":0.5}""").getString("type"))
    @Test fun dateTimerIsCountdown() = assertEquals("countdown", s("""{"type":"timer","to":"2026-12-12T09:00"}""").getString("type"))
    @Test fun minuteTimerStaysTimer() = assertEquals("timer", s("""{"type":"timer","minutes":25}""").getString("type"))
    @Test fun newLeavesKept() {
        assertNotNull(s("""{"type":"metric","value":"3"}"""))
        assertNotNull(s("""{"type":"list","items":[{"title":"a"}]}"""))
        assertNotNull(s("""{"type":"choice","question":"q","options":["a","b"]}"""))
    }
    @Test fun legacyCardsStillLoad() = assertEquals("chip", s("""{"type":"chip","text":"x"}""").getString("type"))
}
