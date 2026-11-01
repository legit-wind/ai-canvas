package com.aicanvas.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

// The three newer building blocks: metric, list and choice. Colours come only from the theme (C) and named tones.

@Composable
internal fun MetricNode(n: JSONObject, modifier: Modifier) {
    val color = paletteColor(n.optString("color"), C.Text)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tokens.XS)) {
        n.optString("label").takeIf { it.isNotBlank() }?.let {
            Text(it.uppercase(), color = C.Muted, style = textStyle("label", ""))
        }
        BigText(n.optString("value"), Modifier, textStyle("display", "").copy(color = color), maxLines = 1)
        n.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun ListNode(n: JSONObject, modifier: Modifier, onOpen: (String) -> Unit) {
    if (n.optString("kind") == "schedule") { ScheduleNode(n, modifier); return }
    val items = n.optJSONArray("items") ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tokens.XS)) {
        for (i in 0 until minOf(items.length(), 20)) {
            val item = items.optJSONObject(i) ?: JSONObject().put("title", items.optString(i))
            val link = item.optString("link").takeIf { it.startsWith("https://") }
            val sub = item.optJSONArray("items") ?: item.optJSONArray("slots")
            if (sub != null && sub.length() > 0) { ExpandableRow(item, sub); continue }
            Row(
                Modifier.fillMaxWidth().heightIn(min = Tokens.TouchTarget).clip(RoundedCornerShape(Tokens.L))
                    .then(if (link != null) Modifier.clickable(role = Role.Button) { onOpen(link) } else Modifier)
                    .padding(vertical = Tokens.S),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.L),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.XS)) {
                    Text(item.optString("title"), color = if (link != null) C.Accent else C.Text, style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.optString("detail").takeIf { it.isNotBlank() }?.let {
                        Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                item.optString("meta").takeIf { it.isNotBlank() }?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

/** A quiz question: tap an option; if the model gave an answer, the right one turns green and a wrong pick red. */
@Composable
internal fun ChoiceNode(n: JSONObject, modifier: Modifier, onPick: (Int) -> Unit) {
    val options = n.optJSONArray("options") ?: return
    val picked = n.optInt("picked", -1)
    val answer = n.optInt("answer", -1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tokens.M)) {
        Text(n.optString("question"), color = C.Text, style = MaterialTheme.typography.titleMedium)
        for (i in 0 until options.length()) {
            val state = when {
                picked < 0 -> 0
                i == answer -> 1
                i == picked && answer >= 0 -> 2
                i == picked -> 3
                else -> 0
            }
            val tint = when (state) { 1 -> C.Good; 2 -> C.Bad; 3 -> ink(); else -> C.Muted }
            val bg by animateColorAsState(if (state == 0) Color.Transparent else tint.copy(alpha = 0.14f), tween(200), label = "choice")
            Text(
                options.optString(i),
                Modifier.fillMaxWidth().heightIn(min = Tokens.TouchTarget).clip(RoundedCornerShape(Tokens.L)).background(bg)
                    .border(Tokens.Hairline, tint.copy(alpha = if (state == 0) 0.35f else 0.8f), RoundedCornerShape(Tokens.L))
                    .clickable(enabled = picked < 0, role = Role.RadioButton) { onPick(i) }
                    .padding(horizontal = Tokens.Radius, vertical = Tokens.L),
                color = C.Text, style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (picked >= 0) n.optString("explain").takeIf { it.isNotBlank() }?.let {
            Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A list row that carries its own sub-rows (e.g. a day with its classes): tap to show or hide them. */
@Composable
private fun ExpandableRow(item: JSONObject, sub: org.json.JSONArray) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(item.optString("title")) { androidx.compose.runtime.mutableStateOf(false) }
    val arrow by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, tween(280), label = "rowArrow")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.L)).animateContentSize(tween(280))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Tokens.TouchTarget).clickable(role = Role.Button) { open = !open }.padding(vertical = Tokens.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.optString("title"), color = C.Text, style = MaterialTheme.typography.bodyLarge)
                item.optString("detail").takeIf { it.isNotBlank() && !open }?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            item.optString("meta").takeIf { it.isNotBlank() }?.let { Text(it, color = C.Muted, style = MaterialTheme.typography.labelMedium) }
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown, if (open) "Hide" else "Show",
                Modifier.rotate(arrow), tint = C.Muted)
        }
        if (open) Column(Modifier.padding(start = Tokens.L, bottom = Tokens.S), verticalArrangement = Arrangement.spacedBy(Tokens.XS)) {
            for (j in 0 until minOf(sub.length(), 20)) {
                val o = sub.optJSONObject(j)
                val left = o?.optString("time")?.ifBlank { o.optString("meta") }.orEmpty()
                val right = o?.let { it.optString("subject").ifBlank { it.optString("title") } } ?: sub.optString(j)
                Row {
                    if (left.isNotBlank()) Text(left, Modifier.width(96.dp), color = C.Muted, style = MaterialTheme.typography.bodySmall)
                    Text(right.ifBlank { "Free" }, color = if (right.isBlank()) C.Muted else C.Text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
