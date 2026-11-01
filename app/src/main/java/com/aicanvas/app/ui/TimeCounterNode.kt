package com.aicanvas.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.aicanvas.app.data.TimeCounter
import kotlinx.coroutines.delay
import org.json.JSONObject

/** A duration counter ("Exercise 30 minutes") as a stopwatch toward its goal: start / pause, reset, live mm:ss. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TimeCounterNode(n: JSONObject, modifier: Modifier, onChange: ((JSONObject) -> Unit) -> Unit) {
    val running = TimeCounter.isRunning(n)
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        nowMs = System.currentTimeMillis()
        while (running) { delay(1000L - nowMs % 1000L); nowMs = System.currentTimeMillis() }
    }
    val elapsed = TimeCounter.elapsedSeconds(n, nowMs)
    val goal = TimeCounter.goalSeconds(n)
    val color = paletteColor(n.optString("color"), ink())
    val haptics = LocalHapticFeedback.current
    val label = n.optString("label").ifBlank { "Timer" }
    val goalText = if (goal > 0) "/ ${TimeCounter.clock(goal)}" else ""
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clearAndSetSemantics {
                    contentDescription = "$label: ${TimeCounter.clock(elapsed)} $goalText ${if (running) "running" else "paused"}"
                    liveRegion = LiveRegionMode.Polite
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(TimeCounter.clock(elapsed), color = color, style = textStyle("display", ""), maxLines = 1)
                if (goalText.isNotEmpty()) Text(goalText, Modifier.padding(start = 6.dp, bottom = 7.dp), color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Row {
                RoundIcon(Icons.Rounded.Refresh, "Reset", color.copy(alpha = if (C.dark) 0.24f else 0.12f), color) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange { TimeCounter.reset(it) }
                }
                Spacer(Modifier.width(10.dp))
                RoundIcon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "Pause" else "Start", color, onColor(color)) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val t = System.currentTimeMillis()
                    onChange { if (TimeCounter.isRunning(it)) TimeCounter.pause(it, t) else TimeCounter.start(it, t) }
                }
            }
        }
        n.optJSONArray("history")?.takeIf { it.length() > 0 }?.let { HistoryBars(it, n.optDouble("goal", 0.0), color) }
        if (goal > 0) {
            ProgressBar(TimeCounter.progress(n, nowMs), color, Modifier.fillMaxWidth(), height = 6.dp)
            if (elapsed >= goal) Text("Goal reached 🎉", color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}
