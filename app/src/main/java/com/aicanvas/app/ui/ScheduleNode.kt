package com.aicanvas.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicanvas.app.data.Schedule
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

private const val ANIM_MS = 280

/** Which timetables (and which day in each) are open, shared by every composition of the same card. */
private object ScheduleOpen {
    val open = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    val day = androidx.compose.runtime.mutableStateMapOf<String, Int>()
}

/** A tappable weekly timetable: collapsed it shows today; open it for day rows; open a day for its slots. */
@Composable
internal fun ScheduleNode(n: JSONObject, modifier: Modifier) {
    // Old saved cards may still use "name"/"classes"/"start"-"end"; normalise a copy so they draw like new ones.
    val days = remember(n.toString()) { Schedule.parse(JSONObject(n.toString()).also { Schedule.adopt(it) }) }
    val tones = remember(days) { Schedule.tones(days) }
    // Ticks every minute so "Now" / "Next" and today stay right while the card is on screen.
    var now by remember { mutableStateOf(LocalTime.now()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now(); date = LocalDate.now() } }
    val today = Schedule.todayIndex(days, date.dayOfWeek)
    // Open state lives outside this composition: the home grid may compose the card twice (a measuring pass and the
    // shown one), and both must agree or the shown card stays at the closed height.
    val key = remember(n.toString()) { n.optString("title") + "|" + days.hashCode() }
    val open = ScheduleOpen.open[key] ?: false
    val openDay = ScheduleOpen.day[key] ?: -2 // -2 = not chosen yet → today
    val shownDay = if (openDay == -2) today else openDay
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(ANIM_MS, easing = FastOutSlowInEasing), label = "arrow")

    Column(modifier.fillMaxWidth().animateContentSize(tween(ANIM_MS, easing = FastOutSlowInEasing))) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { ScheduleOpen.open[key] = !open }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(n.optString("title").ifBlank { "Week timetable" }, style = MaterialTheme.typography.titleMedium, color = C.Text)
                val sub = if (today < 0) "No classes today" else {
                    val d = days[today]
                    Schedule.current(d, now)?.let { (i, tag) -> "$tag: ${d.slots[i].subject} · ${d.slots[i].time}" } ?: "Done for today"
                }
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            }
            Icon(Icons.Rounded.KeyboardArrowDown, if (open) "Collapse" else "Expand", Modifier.rotate(arrow), tint = C.Muted)
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(tween(ANIM_MS)) + fadeIn(tween(ANIM_MS)),
            exit = shrinkVertically(tween(ANIM_MS)) + fadeOut(tween(ANIM_MS / 2)),
        ) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d ->
                    DayRow(d, i == today, i == shownDay, tones, if (i == today) Schedule.current(d, now) else null) {
                        ScheduleOpen.day[key] = if (shownDay == i) -1 else i
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(d: Schedule.Day, isToday: Boolean, expanded: Boolean, tones: Map<String, String>, current: Pair<Int, String>?, onTap: () -> Unit) {
    val ink = LocalInk.current.takeIf { it != Color.Unspecified } ?: C.Text
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(ANIM_MS), label = "dayArrow")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (isToday) ink.copy(alpha = 0.10f) else C.Line.copy(alpha = 0.35f))
            .animateContentSize(tween(ANIM_MS, easing = FastOutSlowInEasing)),
    ) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onTap).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.name, Modifier.width(46.dp), fontWeight = FontWeight.Bold, color = if (isToday) ink else C.Text, style = MaterialTheme.typography.labelLarge)
            if (!expanded) Text(Schedule.summary(d), Modifier.weight(1f), color = C.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            else Text(if (isToday) "Today" else "", Modifier.weight(1f), color = ink, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Rounded.KeyboardArrowDown, if (expanded) "Hide ${d.name}" else "Show ${d.name}", Modifier.size(20.dp).rotate(arrow), tint = C.Muted)
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (d.slots.isEmpty()) Text("No classes", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                d.slots.forEachIndexed { i, s -> SlotRow(s, tones, current?.takeIf { it.first == i }?.second) }
            }
        }
    }
}

@Composable
private fun SlotRow(s: Schedule.Slot, tones: Map<String, String>, tag: String?) {
    val lunch = s.subject.equals("lunch", true)
    val color = if (s.free || lunch) C.Muted else Tones.ink(tones[Schedule.base(s.subject)]) ?: C.Text
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (tag != null) color.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.time, Modifier.width(92.dp), color = C.Muted, style = MaterialTheme.typography.bodySmall)
        if (!s.free && !lunch) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(
            if (s.free) "Free" else s.subject,
            Modifier.weight(1f).padding(start = 8.dp),
            color = if (s.free || lunch) C.Muted else C.Text,
            fontWeight = if (s.free || lunch) FontWeight.Normal else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (tag != null) Text(tag, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
