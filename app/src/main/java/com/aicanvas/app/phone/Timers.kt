package com.aicanvas.app.phone

import android.content.Context
import com.aicanvas.app.data.Element
import org.json.JSONObject

/**
 * Card timers keep their state ("endsAt", "left") inside the card's ui, which is saved with the card.
 * The alarm that rings when a timer ends is not saved by Android, so these helpers book or cancel
 * those alarms from the saved cards (after a reboot, a delete, an undo or an AI change).
 */
object Timers {
    /** The alarm id for the timer at [path] inside card [elementId]. */
    fun key(elementId: String, path: List<Int>) = "timer-$elementId-${path.joinToString("-")}"

    /** Calls [block] for every timer node inside [ui], with its path of child indexes. */
    fun forEach(ui: JSONObject, path: List<Int> = emptyList(), block: (List<Int>, JSONObject) -> Unit) {
        if (ui.optString("type") == "timer") block(path, ui)
        val children = ui.optJSONArray("children") ?: return
        for (i in 0 until children.length()) children.optJSONObject(i)?.let { forEach(it, path + i, block) }
    }

    /** Books the end alarm for every timer in [element] that is still running. */
    fun scheduleRunning(context: Context, element: Element, now: Long = System.currentTimeMillis()) {
        forEach(element.ui) { path, node ->
            val endsAt = node.optLong("endsAt")
            if (endsAt > now) Reminders.schedule(context, key(element.id, path), endsAt, "⏱ ${node.optString("label", "Timer")} is done")
        }
    }

    /** Cancels the end alarm of every timer in [element]. */
    fun cancelAll(context: Context, element: Element) {
        forEach(element.ui) { path, _ -> Reminders.cancel(context, key(element.id, path)) }
    }
}
