package com.aicanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.ai.ReplyParser
import java.util.Locale

/** Shows each step of recent AI runs: context, tool calls, validation, repair, result. Session only. */
@Composable
fun ActivityPage(vm: AppViewModel) {
    var openId by remember { mutableStateOf<Long?>(null) }
    var showJson by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "What happened on each request this session: context → model → tools → checks → canvas. These are recorded steps, not the model's private thinking.",
            color = C.Muted, style = MaterialTheme.typography.bodyMedium,
        )
        if (vm.runs.isEmpty()) {
            Text("Ask for a card, then come back here to see how it was made.", color = C.Text, style = MaterialTheme.typography.bodyLarge)
        }
        vm.runs.forEach { run ->
            val open = openId == run.id
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)
                    .clickable { openId = if (open) null else run.id; showJson = false }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val time = run.elapsedMs?.let { String.format(Locale.US, "%.1f s", it / 1000.0) } ?: "running"
                Text("${run.outcome} · $time · ${run.model}", color = if (run.outcome == "Failed") C.Bad else C.Muted, style = MaterialTheme.typography.labelMedium)
                Text(run.prompt, color = C.Text, style = MaterialTheme.typography.bodyLarge, maxLines = if (open) 20 else 2)
                if (open) {
                    run.events.forEachIndexed { i, e ->
                        Text("${i + 1}. ${e.label}", color = C.Accent, style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(e.detail, color = C.Text, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (run.raw.isNotBlank()) {
                        Text(if (showJson) "Hide JSON" else "Show JSON", Modifier.clickable { showJson = !showJson }.padding(vertical = 8.dp),
                            color = C.Accent, style = MaterialTheme.typography.labelLarge)
                        if (showJson) {
                            val pretty = remember(run.raw) { ReplyParser.jsonIn(run.raw)?.toString(2) ?: run.raw }
                            SelectionContainer {
                                Text(pretty, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Bg).padding(12.dp),
                                    color = C.Text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            }
                        }
                    }
                }
            }
        }
    }
}
