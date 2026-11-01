package com.aicanvas.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSchemaTest {
    @Test fun rejectsUnknownRootType() {
        assertNull(UiSchema.sanitize(JSONObject("""{"type":"webview"}""")))
        assertNull(UiSchema.sanitize("not an object"))
        assertNull(UiSchema.sanitize(null))
    }

    @Test fun dropsBadChildrenButKeepsTheCard() {
        val ui = UiSchema.sanitize(
            JSONObject("""{"type":"column","children":[{"type":"text","text":"hi"},{"type":"script"},5,{"type":"check","label":"a"}]}"""),
        )!!
        val kids = ui.getJSONArray("children")
        assertEquals(2, kids.length())
        assertEquals("check", kids.getJSONObject(1).getString("type"))
    }

    @Test fun limitsDepthAndText() {
        var deep = JSONObject("""{"type":"text","text":"${"x".repeat(5000)}"}""")
        repeat(20) { deep = JSONObject().put("type", "box").put("children", org.json.JSONArray().put(deep)) }
        val ui = UiSchema.sanitize(deep)!!
        var n = ui
        var depth = 0
        while (n.optJSONArray("children")?.length() ?: 0 > 0) { n = n.getJSONArray("children").getJSONObject(0); depth++ }
        assertTrue(depth <= UiSchema.MAX_DEPTH)
        val short = UiSchema.sanitize(JSONObject("""{"type":"text","text":"${"x".repeat(5000)}"}"""))!!
        assertEquals(UiSchema.MAX_TEXT, short.getString("text").length)
    }

    @Test fun keepsTicksCountsAndTimers() {
        val old = JSONObject(
            """{"type":"column","children":[
              {"type":"check","label":"Milk","checked":true},
              {"type":"counter","label":"Water","value":5},
              {"type":"timer","label":"Focus","endsAt":123,"left":0}]}""",
        )
        val new = JSONObject(
            """{"type":"column","children":[
              {"type":"text","text":"Updated"},
              {"type":"check","label":"milk","checked":false},
              {"type":"check","label":"Eggs"},
              {"type":"counter","label":"Water","value":0},
              {"type":"timer","label":"Focus","start":true}]}""",
        )
        val kids = UiSchema.keepUserState(old, new).getJSONArray("children")
        assertTrue(kids.getJSONObject(1).getBoolean("checked"))
        assertFalse(kids.getJSONObject(2).optBoolean("checked"))
        assertEquals(5.0, kids.getJSONObject(3).getDouble("value"), 0.0)
        assertEquals(123L, kids.getJSONObject(4).getLong("endsAt"))
        assertFalse(kids.getJSONObject(4).has("start"))
    }
}
