package com.aicanvas.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import org.json.JSONObject

// The individual node widgets drawn by UiNode.
@Composable
internal fun CheckLine(n: JSONObject, modifier: Modifier, onToggle: () -> Unit) {
    val done = n.optBoolean("checked")
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (done) ink() else Color.Transparent, tween(180), label = "check fill")
    val ring by animateColorAsState(if (done) ink() else C.Muted.copy(alpha = 0.6f), tween(180), label = "check ring")
    val textColor by animateColorAsState(if (done) C.Muted else C.Text, tween(180), label = "check text")
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(done, role = Role.Checkbox) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(fill).border(1.75.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The tick pops in with a little bounce.
            val pop by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "tick")
            Icon(Icons.Rounded.Check, null, tint = C.OnAccent, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) })
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                n.optString("label"),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            n.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium) }
            // A daily tick (medicine, habit) shows its log for the last days.
            val (taken, days) = com.aicanvas.app.data.CardLogic.takenDays(n.optJSONArray("history"))
            if (days > 0) Text("Done $taken of last $days days", color = C.Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A rounded bar that slides to its new value. TalkBack reads it as a progress bar. */
@Composable
internal fun ProgressBar(value: Float, color: Color, modifier: Modifier, height: Dp = 8.dp, label: String = "") {
    val v by animateFloatAsState(value.coerceIn(0f, 1f), tween(500), label = "progress")
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(color.copy(alpha = if (C.dark) 0.25f else 0.15f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                if (label.isNotBlank()) contentDescription = label
            },
    ) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
internal fun Countdown(n: JSONObject, modifier: Modifier) {
    val target = remember(n.optString("to")) { runCatching { LocalDateTime.parse(n.optString("to")) }.getOrNull() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(target) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }
    // Keyed on the ticking clock so the text refreshes every 30 seconds.
    val text = remember(target, now) {
        if (target == null) "–" else {
            val left = Duration.between(LocalDateTime.now(), target)
            when {
                left.isNegative -> "Now"
                left.toDays() >= 1 -> "${left.toDays()}d ${left.toHours() % 24}h"
                left.toHours() >= 1 -> "${left.toHours()}h ${left.toMinutes() % 60}m"
                else -> "${left.toMinutes().coerceAtLeast(1)}m"
            }
        }
    }
    val style = textStyle(n.optString("style", "display"), n.optString("weight")).copy(color = paletteColor(n.optString("color"), C.Text))
    BigText(text, modifier, style, maxLines = 1)
}

/** Large text that shrinks to fit narrow cards instead of breaking onto extra lines. */
@Composable
internal fun BigText(text: String, modifier: Modifier, style: TextStyle, maxLines: Int) {
    // Reserve the full-size line height: auto-sized text reports its smallest size when cards are measured,
    // which would make the card too short and squash whatever sits below.
    val lineHeight = with(LocalDensity.current) { (style.fontSize * 1.1f).toDp() }
    BasicText(
        text,
        modifier.height(lineHeight * maxLines),
        style = style.copy(lineHeight = style.fontSize * 1.1f),
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = style.fontSize),
    )
}

internal fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)

@Composable
internal fun Counter(n: JSONObject, modifier: Modifier, onStep: (Double) -> Unit) {
    val value = n.optDouble("value", 0.0)
    val step = n.optDouble("step", 1.0).takeIf { it > 0 } ?: 1.0
    val goal = n.optDouble("goal", 0.0)
    val color = paletteColor(n.optString("color"), ink())
    val haptics = LocalHapticFeedback.current
    val step1 = { d: Double -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onStep(d) }
    val unitText = n.optString("unit")
    val spoken = buildString {
        append(n.optString("label").ifBlank { "Count" }).append(": ").append(num(value))
        if (goal > 0) append(" of ").append(num(goal))
        if (unitText.isNotBlank()) append(" ").append(unitText)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The number and the buttons share a line when there's room; in a narrow card the buttons go underneath.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.clearAndSetSemantics { contentDescription = spoken; liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.Bottom) {
                AnimatedContent(
                    value,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "counter",
                ) { Text(num(it), color = color, style = textStyle("display", ""), maxLines = 1) }
                val unit = listOfNotNull(if (goal > 0) "/ ${num(goal)}" else null, n.optString("unit").ifBlank { null }).joinToString(" ")
                if (unit.isNotEmpty()) Text(unit, Modifier.padding(start = 6.dp, bottom = 7.dp), color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Row {
                RoundIcon(Icons.Rounded.Remove, "Decrease", color.copy(alpha = if (C.dark) 0.24f else 0.12f), color) { if (value - step >= 0) step1(-step) }
                Spacer(Modifier.width(10.dp))
                RoundIcon(Icons.Rounded.Add, "Increase", color, onColor(color)) { step1(step) }
            }
        }
        n.optJSONArray("history")?.takeIf { it.length() > 0 }?.let { HistoryBars(it, goal, color) }
        val streak = com.aicanvas.app.data.CardLogic.streak(n.optJSONArray("history"), goal, value)
        if (streak >= 2) Text("🔥 $streak-day streak", color = color, style = MaterialTheme.typography.labelLarge)
        if (goal > 0) {
            ProgressBar((value / goal).toFloat(), color, Modifier.fillMaxWidth(), height = 6.dp)
        }
    }
}

/** The last few days of a daily counter as small bars, with the day underneath. */
@Composable
internal fun HistoryBars(history: org.json.JSONArray, goal: Double, color: Color) {
    val days = (0 until history.length()).mapNotNull { history.optJSONObject(it) }
    val top = maxOf(goal, days.maxOf { it.optDouble("value", 0.0) }, 1.0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        // Always seven slots, newest on the right, so a short history still has narrow bars.
        repeat(7 - days.size) { Spacer(Modifier.weight(1f)) }
        days.forEach { d ->
            val v = d.optDouble("value", 0.0)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((36 * (v / top)).coerceAtLeast(3.0).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (goal > 0 && v >= goal) color else color.copy(alpha = 0.35f))
                )
                Text(d.optString("day").take(3), color = C.Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun Ring(n: JSONObject, modifier: Modifier) {
    val target = n.optDouble("value", 0.0).toFloat().coerceIn(0f, 1f)
    val v by animateFloatAsState(target, tween(700), label = "ring")
    val color = paletteColor(n.optString("color"), ink())
    val size = n.optInt("size", 84).coerceIn(48, 140).dp
    val spoken = n.optString("label").ifBlank { "${(target * 100).toInt()} percent" }
    Box(
        modifier.size(size).clearAndSetSemantics {
            contentDescription = spoken
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(color.copy(alpha = if (C.dark) 0.25f else 0.15f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * v, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val label = n.optString("label").ifBlank { "${(target * 100).toInt()}%" }
        // Fixed-size ring, so the label shrinks with large fonts instead of spilling out.
        BasicText(
            label,
            Modifier.padding(horizontal = size * 0.18f),
            style = TextStyle(color = C.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = (size.value * 0.2f).sp),
        )
    }
}

@Composable
internal fun Chart(n: JSONObject, modifier: Modifier) {
    val arr = n.optJSONArray("values") ?: return
    val values = (0 until arr.length()).map { arr.optDouble(it, 0.0).toFloat() }
    if (values.isEmpty()) return
    val labels = n.optJSONArray("labels")?.let { l -> (0 until l.length()).map { l.optString(it) } } ?: emptyList()
    val color = paletteColor(n.optString("color"), ink())
    val max = values.max().takeIf { it > 0f } ?: 1f
    // Grow in when first shown or when the numbers change.
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(600)) }
    val spoken = n.optString("title").ifBlank { "Chart" } + ": " +
        values.mapIndexed { i, v -> (labels.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { "$it " } ?: "") + num(v.toDouble()) }.joinToString(", ")
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (n.optString("kind") == "line") {
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val dx = if (values.size > 1) size.width / (values.size - 1) else 0f
                val pad = 6.dp.toPx()
                val points = values.mapIndexed { i, v -> Offset(i * dx, size.height - pad - v / max * (size.height - 2 * pad) * grow.value) }
                val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                // Soft fill under the line, fading down.
                val area = Path().apply { addPath(path); lineTo(points.last().x, size.height); lineTo(points.first().x, size.height); close() }
                drawPath(area, color.copy(alpha = 0.12f))
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(C.Card, 6.dp.toPx(), points.last())
                drawCircle(color, 4.dp.toPx(), points.last())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(((v / max) * grow.value).coerceIn(0.03f, 1f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(if (i == values.lastIndex) color else color.copy(alpha = if (C.dark) 0.45f else 0.35f))
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.take(values.size).forEach {
                    Text(it, Modifier.weight(1f), color = C.Muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun TimerNode(n: JSONObject, modifier: Modifier, onFinished: () -> Unit = {}, onChange: (endsAt: Long, left: Long) -> Unit) {
    val total = (n.optDouble("minutes", 25.0) * 60_000).toLong().coerceAtLeast(1_000)
    val endsAt = n.optLong("endsAt")
    val paused = n.optLong("left")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt > 0) { now = System.currentTimeMillis(); delay(250) }
    }
    val running = endsAt > now
    val finished = endsAt in 1..now
    val left = when { running -> endsAt - now; paused > 0 -> paused; else -> total }
    val color = paletteColor(n.optString("color"), ink())
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val secs = (left + 999) / 1000
        val name = n.optString("label", "Timer")
        // Spoken once per minute-ish change, not every tick.
        val spoken = when { finished -> "$name done"; running -> "$name running, ${(secs + 59) / 60} minutes left"; paused > 0 -> "$name paused"; else -> "$name, ${(total / 60_000)} minutes" }
        BigText(
            if (finished) "Done" else "%d:%02d".format(secs / 60, secs % 60),
            Modifier.clearAndSetSemantics { contentDescription = spoken },
            textStyle("display", "").copy(color = color),
            maxLines = 1,
        )
        ProgressBar(if (running || paused > 0) 1f - left.toFloat() / total else if (finished) 1f else 0f, color, Modifier.fillMaxWidth(), height = 6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (running) "Pause" else "Start", color, filled = true) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val t = System.currentTimeMillis()
                if (running) onChange(0, endsAt - t) else onChange(t + if (finished) total else left, 0)
            }
            if (endsAt > 0 || paused > 0) Pill("Reset", color, filled = false) { onChange(0, 0) }
        }
        if (n.optBoolean("log")) {
            val sessions = n.optInt("sessions")
            Text(if (sessions == 0) "No sessions yet today" else "🍅 $sessions session${if (sessions == 1) "" else "s"} today", color = C.Muted, style = MaterialTheme.typography.labelMedium)
            // Count a finished session right away, even with nothing tapped.
            if (finished && !n.optBoolean("counted")) LaunchedEffect(endsAt) { onFinished() }
        }
    }
}

@Composable
internal fun Pill(label: String, color: Color, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = if (C.dark) 0.24f else 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled) onColor(color) else color, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun InputLine(n: JSONObject, modifier: Modifier, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onSubmit(text.trim()); text = "" } }
    Row(
        // A fixed height: cards in a row are sized by measuring their content, and text fields under-report theirs.
        modifier.heightIn(min = 52.dp).clip(CircleShape).background(C.Raised).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            text,
            { text = it },
            Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Text),
            cursorBrush = SolidColor(C.Text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(n.optString("placeholder", "Type here…"), color = C.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        RoundIcon(Icons.Rounded.ArrowUpward, "Send", if (text.isBlank()) C.Card else C.Accent, if (text.isBlank()) C.Muted else C.OnAccent) { submit() }
    }
}
