package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Card edits the phone makes by itself, without asking the AI. Pure, so they are unit-tested. */
object LocalEdits {
    /** Inserts a new unticked check just above the input at [inputIndex] inside [parent]. Existing ticks are kept. */
    fun addCheck(parent: JSONObject, inputIndex: Int, label: String) {
        val children = parent.optJSONArray("children") ?: return
        val text = label.trim().take(200)
        if (text.isEmpty() || children.optJSONObject(inputIndex)?.optString("type") != "input") return
        val updated = JSONArray()
        for (i in 0 until children.length()) {
            if (i == inputIndex) updated.put(JSONObject().put("type", "check").put("label", text).put("checked", false))
            updated.put(children.get(i))
        }
        parent.put("children", updated)
    }
}
