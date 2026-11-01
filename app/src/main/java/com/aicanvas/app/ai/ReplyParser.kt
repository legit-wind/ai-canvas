package com.aicanvas.app.ai

import com.aicanvas.app.data.Element
import com.aicanvas.app.data.Reminder
import com.aicanvas.app.data.UiSchema
import com.aicanvas.app.phone.Reminders
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/** Reads the AI's JSON answer. No Android or network code here, so it is unit-tested. */
object ReplyParser {
    /** The JSON object in a reply, tolerating code fences, text around it and trailing commas. */
    fun jsonIn(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = raw.substring(start, end + 1)
        return (runCatching { JSONObject(body) }.getOrNull()
            ?: runCatching { JSONObject(body.replace(Regex(",\\s*([}\\]])"), "$1")) }.getOrNull())
            ?.also { com.aicanvas.app.data.Schedule.adoptAll(it) }
    }

    /** Turns the AI's raw answer into a [Reply]. Plain text (no JSON) becomes a text-only reply. */
    fun parse(raw: String, now: Long = System.currentTimeMillis()): Reply {
        val o = jsonIn(raw)
            ?: return Reply(raw.trim(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), raw)

        val create = o.optJSONArray("create").objects().mapNotNull { e ->
            val ui = UiSchema.sanitize(e.opt("ui")) ?: return@mapNotNull null
            Element(
                size = sizeOf(e.optString("size")) ?: "full",
                tone = e.optString("tone", "none"),
                ui = ui,
                importance = e.optDouble("importance", 50.0).coerceIn(0.0, 100.0),
                refresh = e.optJSONObject("refresh")?.takeIf { it.optString("kind") in setOf("weather", "news", "fx", "daily") },
            )
        }
        var pastReminders = 0
        val remind = o.optJSONArray("remind").objects().mapNotNull { r ->
            val at = runCatching {
                LocalDateTime.parse(r.optString("at").trim()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull() ?: return@mapNotNull null
            val repeat = r.optString("repeat").trim().lowercase()
                .let { if (it == "hourly") "hours:1" else it }
                .takeIf { it == "daily" || it == "weekly" || Regex("hours:([1-9]|1[0-2])").matches(it) } ?: ""
            val next = if (repeat.isEmpty()) at.takeIf { it > now } ?: run { pastReminders++; return@mapNotNull null } else Reminders.nextAfter(at, repeat, now)
            Reminder(at = next, text = r.optString("text").trim().ifBlank { "Reminder" }, repeat = repeat)
        }
        return Reply(
            text = if (o.isNull("reply")) "" else o.optString("reply").trim(),
            create = create,
            // An update with a broken design is dropped; size/tone/importance-only updates are kept.
            update = o.optJSONArray("update").objects().mapNotNull { u ->
                if (!u.has("ui")) return@mapNotNull u
                val ui = UiSchema.sanitize(u.opt("ui")) ?: return@mapNotNull null
                u.put("ui", ui)
            },
            remove = o.optJSONArray("remove").strings(),
            remember = o.optJSONArray("remember").strings().filter { it.isNotBlank() },
            forget = o.optJSONArray("forget").strings(),
            remind = remind,
            cancelReminders = o.optJSONArray("cancel_reminders").strings(),
            raw = raw,
            pastReminders = pastReminders,
            add = o.optJSONObject("add")?.let { a ->
                listOf("tasks", "habits", "countdowns", "expenses").associateWith { k ->
                    val arr = a.optJSONArray(k) ?: return@associateWith emptyList()
                    (0 until arr.length()).mapNotNull { i ->
                        when (val x = arr.opt(i)) {
                            is JSONObject -> x
                            is String -> x.trim().takeIf { it.isNotEmpty() }?.let { t ->
                                JSONObject().put(if (k == "habits") "name" else if (k == "expenses") "text" else "title", t)
                            }
                            else -> null
                        }
                    }
                }.filterValues { it.isNotEmpty() }
            } ?: emptyMap(),
            removeAll = (o.optJSONArray("remove_all")?.strings() ?: listOfNotNull(o.optString("remove_all").takeIf { it.isNotBlank() })).filter { it.isNotBlank() },
        )
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    this?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } } ?: emptyList()

internal fun JSONArray?.strings(): List<String> =
    this?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() } } ?: emptyList()
