package com.aicanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * Draws a tree of UI building blocks that the AI designed (see Ai.SYSTEM for the vocabulary).
 * Unknown types are skipped, so a slightly-off design still renders.
 */
class RenderActions(
    /** Change a node in place (tick a check, step a counter). [path] is the child indexes from the root node to it. */
    val onUpdate: (path: List<Int>, change: (JSONObject) -> Unit) -> Unit,
    /** Ask the AI to react to a button or input inside the element. */
    val onPress: (label: String, prompt: String) -> Unit,
    /** Open something on the phone: tel:, https:, geo:, alarm:HH:mm. */
    val onOpen: (uri: String) -> Unit,
    /** Start (endsAt > 0), pause (left > 0) or reset a timer. */
    val onTimer: (path: List<Int>, endsAt: Long, left: Long, label: String) -> Unit,
)

fun paletteColor(name: String?, fallback: Color = C.Text): Color = Tones.ink(name) ?: when (name?.lowercase()?.trim()) {
    "blue" -> Color(0xFF3B82F6)
    "indigo" -> Color(0xFF6366F1)
    "purple" -> Color(0xFF9B5DE5)
    "pink" -> Color(0xFFEC4899)
    "red" -> Color(0xFFEF4444)
    "orange" -> Color(0xFFF97316)
    "yellow" -> Color(0xFFE2A400)
    "green" -> Color(0xFF22A45D)
    "teal" -> Color(0xFF14A3A3)
    "gray", "grey" -> C.Muted
    "black" -> C.Text
    null, "" -> fallback
    // Only named colours: a raw hex from the model would ignore dark mode and contrast.
    else -> fallback
}

/** The card background for an element's tone: a soft tint over white, or the plain grey card. */
fun toneBackground(tone: String): Color = Tones.tint(tone)

private fun JSONObject.dp(key: String, default: Int) = optInt(key, default).coerceIn(0, 48).dp

@Composable
fun UiNode(n: JSONObject, actions: RenderActions, path: List<Int> = emptyList(), modifier: Modifier = Modifier) {
    when (n.optString("type")) {
        "column" -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(n.dp("gap", 8)),
            horizontalAlignment = when (n.optString("align")) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            },
        ) { Children(n, actions, path) { c, p -> UiNode(c, actions, p, Modifier.fillMaxWidthIfNeeded(c)) } }

        "row" -> {
            val spread = n.optBoolean("spread")
            // Wraps like text: whatever doesn't fit moves to the next line instead of squeezing its neighbours.
            FlowRow(
                modifier.fillMaxWidth(),
                horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(n.dp("gap", 10)),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = when (n.optString("align")) {
                    "top" -> Alignment.Top
                    "bottom" -> Alignment.Bottom
                    else -> Alignment.CenterVertically
                },
            ) { RowChildren(n, actions, path) }
        }

        "box" -> Column(
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(paletteColor(n.optString("color"), C.Muted).copy(alpha = if (C.dark) 0.18f else 0.10f).compositeOver(C.Card))
                .padding(n.dp("padding", 14)),
            verticalArrangement = Arrangement.spacedBy(n.dp("gap", 6)),
        ) { Children(n, actions, path) { c, p -> UiNode(c, actions, p) } }

        "text" -> {
            val style = textStyle(n.optString("style"), n.optString("weight"))
            val color = paletteColor(n.optString("color"), if (n.optString("style") in setOf("caption", "label")) C.Muted else C.Text)
            val raw = n.optString("text")
            val align = when (n.optString("align")) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }
            if (n.optString("style") == "display") {
                BigText(raw, modifier, style.copy(color = color, textAlign = align), maxLines = 1)
            } else {
                Text(if (n.optString("style") == "label") raw.uppercase() else raw, modifier, color = color, style = style, textAlign = align)
            }
        }

        "check" -> CheckLine(n, modifier) { actions.onUpdate(path) { it.put("checked", !it.optBoolean("checked")) } }

        "counter" -> if (com.aicanvas.app.data.TimeCounter.isTime(n)) TimeCounterNode(n, modifier) { change -> actions.onUpdate(path, change) }
        else Counter(n, modifier) { delta -> actions.onUpdate(path) { it.put("value", it.optDouble("value", 0.0) + delta) } }

        "ring" -> Ring(n, modifier)

        "chart" -> Chart(n, modifier)

        "timer" -> TimerNode(n, modifier, onFinished = { actions.onUpdate(path) { } }) { endsAt, left -> actions.onTimer(path, endsAt, left, n.optString("label", "Timer")) }

        "input" -> InputLine(n, modifier) { text ->
            if (n.optString("add") == "check" && path.isNotEmpty()) {
                // Adds the item on the phone: instant, works offline, keeps every tick.
                actions.onUpdate(path.dropLast(1)) { parent -> com.aicanvas.app.data.LocalEdits.addCheck(parent, path.last(), text) }
            } else actions.onPress(n.optString("placeholder", "input"), n.optString("prompt", "Add") + ": " + text)
        }

        "progress" -> ProgressBar(
            n.optDouble("value", 0.0).toFloat(),
            paletteColor(n.optString("color"), ink()),
            modifier.fillMaxWidth(),
            height = 10.dp,
            label = n.optString("label"),
        )

        "countdown" -> Countdown(n, modifier)

        "chip" -> {
            val color = paletteColor(n.optString("color"), C.Muted)
            Text(
                n.optString("text"),
                modifier.clip(CircleShape).background(color.copy(alpha = if (C.dark) 0.24f else 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
                color = color, style = MaterialTheme.typography.labelMedium,
            )
        }

        "emoji" -> Text(n.optString("text"), modifier, fontSize = n.optInt("size", 28).coerceIn(12, 72).sp)

        "divider" -> Box(modifier.fillMaxWidth().height(1.dp).background(C.Line.copy(alpha = 0.7f)))

        "spacer" -> Spacer(modifier.size(n.dp("size", 8)))

        "button" -> {
            val label = n.optString("label")
            val bg = paletteColor(n.optString("color"), ink())
            Box(
                modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(role = Role.Button) {
                        val open = n.optString("open")
                        if (open.isNotBlank()) actions.onOpen(open) else actions.onPress(label, n.optString("prompt", label))
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (n.optString("color").isBlank()) C.OnAccent else onColor(bg), style = MaterialTheme.typography.labelLarge) }
        }

        "metric" -> MetricNode(n, modifier)

        "list" -> ListNode(n, modifier, actions.onOpen)

        "choice" -> ChoiceNode(n, modifier) { picked -> actions.onUpdate(path) { it.put("picked", picked) } }

        // Anything the validator let through but this version can't draw: say so quietly instead of a blank gap.
        else -> Text("This part can't be shown", modifier, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Children(n: JSONObject, actions: RenderActions, path: List<Int>, child: @Composable (JSONObject, List<Int>) -> Unit) {
    val arr = n.optJSONArray("children") ?: return
    for (i in 0 until arr.length()) {
        val c = arr.optJSONObject(i) ?: continue
        child(c, path + i)
    }
}

/** Inside a row, bars and inputs take the rest of their line; everything else keeps its natural size. */
@Composable
private fun FlowRowScope.RowChildren(n: JSONObject, actions: RenderActions, path: List<Int>) {
    Children(n, actions, path) { c, p ->
        val m = when (c.optString("type")) {
            "progress", "input" -> Modifier.weight(1f)
            else -> Modifier
        }
        UiNode(c, actions, p, m)
    }
}

/** Bars, dividers and panels stretch across a column; text keeps its own width so alignment works. */
private fun Modifier.fillMaxWidthIfNeeded(child: JSONObject): Modifier =
    if (child.optString("type") in setOf("row", "box", "check", "progress", "divider", "chart", "input", "counter", "list", "choice")) fillMaxWidth() else this

internal fun textStyle(style: String, weight: String): TextStyle {
    val base = when (style) {
        "display" -> TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        "title" -> TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        "caption" -> TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
        "label" -> TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        else -> TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    }
    return when (weight) {
        "bold" -> base.copy(fontWeight = FontWeight.Bold)
        "medium" -> base.copy(fontWeight = FontWeight.Medium)
        "regular" -> base.copy(fontWeight = FontWeight.Normal)
        else -> base
    }
}
