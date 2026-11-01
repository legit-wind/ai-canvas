package com.aicanvas.app.phone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.aicanvas.app.MainActivity
import com.aicanvas.app.R
import com.aicanvas.app.data.Element
import com.aicanvas.app.data.Store
import com.aicanvas.app.data.byImportance
import com.aicanvas.app.ui.paletteColor
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime

/** A home-screen widget with the three most important cards, each as a title and its key line. */
class HomeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        private val ROWS = listOf(
            Triple(R.id.row1, R.id.title1, R.id.detail1),
            Triple(R.id.row2, R.id.title2, R.id.detail2),
            Triple(R.id.row3, R.id.title3, R.id.detail3),
        )

        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidget::class.java))
            if (ids.isEmpty()) return
            val top = Store(context).loadElements().sortedWith(byImportance).take(ROWS.size)
            val views = RemoteViews(context.packageName, R.layout.home_widget)
            ROWS.forEachIndexed { i, (row, title, detail) ->
                val e = top.getOrNull(i)
                if (e == null) {
                    views.setViewVisibility(row, View.GONE)
                } else {
                    val (t, d) = summarize(e)
                    views.setViewVisibility(row, View.VISIBLE)
                    views.setTextViewText(title, t)
                    views.setTextViewText(detail, d)
                    views.setViewVisibility(detail, if (d.isBlank()) View.GONE else View.VISIBLE)
                    val accent = if (e.tone.isBlank() || e.tone == "none") context.getColor(R.color.widget_muted) else paletteColor(e.tone).toArgb()
                    views.setTextColor(detail, accent)
                }
            }
            views.setViewVisibility(R.id.empty, if (top.isEmpty()) View.VISIBLE else View.GONE)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.root, open)
            manager.updateAppWidget(ids, views)
        }

        /** A card's headline and its single most useful line (a number, time left, or what's next). */
        fun summarize(e: Element): Pair<String, String> {
            val texts = mutableListOf<JSONObject>()
            val checks = mutableListOf<JSONObject>()
            var key: String? = null
            fun walk(n: JSONObject) {
                when (n.optString("type")) {
                    "text" -> texts += n
                    "check" -> checks += n
                    "countdown" -> if (key == null) key = timeLeft(n.optString("to"))
                    "counter" -> if (key == null) {
                        val goal = n.optDouble("goal", 0.0)
                        key = listOfNotNull(fmt(n.optDouble("value", 0.0)), if (goal > 0) "/ ${fmt(goal)}" else null, n.optString("unit").ifBlank { null })
                            .joinToString(" ")
                    }
                }
                n.optJSONArray("children")?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::walk) }
            }
            walk(e.ui)
            val title = (texts.firstOrNull { it.optString("style") == "title" } ?: texts.firstOrNull())?.optString("text")
                ?: checks.firstOrNull()?.optString("label") ?: ""
            val display = texts.firstOrNull { it.optString("style") == "display" }?.optString("text")
            val open = checks.filter { !it.optBoolean("checked") }
            val todo = if (checks.isEmpty()) null else if (open.isEmpty()) "All done ✓" else "Next: ${open.first().optString("label")}"
            val other = texts.map { it.optString("text") }.firstOrNull { it != title }
            return title to (key ?: display ?: todo ?: other ?: "")
        }

        private fun timeLeft(to: String): String? {
            val target = runCatching { LocalDateTime.parse(to) }.getOrNull() ?: return null
            val left = Duration.between(LocalDateTime.now(), target)
            return when {
                left.isNegative -> "Now"
                left.toDays() >= 1 -> "in ${left.toDays()}d ${left.toHours() % 24}h"
                left.toHours() >= 1 -> "in ${left.toHours()}h ${left.toMinutes() % 60}m"
                else -> "in ${left.toMinutes().coerceAtLeast(1)}m"
            }
        }

        private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
    }
}
