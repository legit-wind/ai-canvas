package com.aicanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.Habit
import com.aicanvas.app.data.Habits
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val WEEKS = 12

/** Settings → Habits: tick today, see streaks, a 12-week heatmap and a 30-day rate, all from what you logged. */
@Composable
fun HabitsPage(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Habit?>(null) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Button({ editing = Habit(name = "") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add habit")
        }
        Spacer(Modifier.height(10.dp))
        if (vm.habits.isEmpty()) Text("No habits yet. Add one, then tick it each day to build a streak.", color = C.Muted, modifier = Modifier.padding(top = 32.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.habits, key = { it.id }) { h -> HabitCard(h, today, { vm.saveHabit(Habits.toggle(h, today)) }) { editing = h } }
        }
    }
    editing?.let { h -> HabitEditor(h, vm.habits.none { it.id == h.id }, { editing = null }, { vm.saveHabit(it); editing = null }, { vm.deleteHabit(h.id); editing = null }) }
}

@Composable
private fun HabitCard(h: Habit, today: LocalDate, onTick: () -> Unit, onEdit: () -> Unit) {
    val doneToday = today.toString() in h.done
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).clickable(onClick = onEdit).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium, color = C.Text)
                val rate = Habits.rate(h, today)
                Text(
                    "🔥 ${Habits.streak(h, today)} streak · best ${Habits.best(h)}" +
                        (rate?.let { " · ${it.first}/${it.second} in 30 days" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = C.Muted,
                )
            }
            val due = Habits.isDue(h, today)
            FilledIconToggleButton(doneToday, { onTick() }, Modifier.semantics { contentDescription = if (doneToday) "Done today, tap to undo" else "Mark done today" }) {
                Icon(Icons.Rounded.Check, null)
            }
            if (!due && !doneToday) Text("rest day", style = MaterialTheme.typography.labelSmall, color = C.Muted)
        }
        Spacer(Modifier.height(10.dp))
        // Heatmap: columns are weeks (oldest left), rows Monday..Sunday.
        val start = today.minusWeeks((WEEKS - 1).toLong()).with(java.time.DayOfWeek.MONDAY)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(WEEKS) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { d ->
                        val day = start.plusDays((w * 7 + d).toLong())
                        val color = when {
                            day.isAfter(today) -> C.Raised.copy(alpha = 0.3f)
                            day.toString() in h.done -> C.Good
                            Habits.isDue(h, day) -> C.Raised
                            else -> C.Raised.copy(alpha = 0.4f)
                        }
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditor(start: Habit, isNew: Boolean, onDismiss: () -> Unit, onSave: (Habit) -> Unit, onDelete: () -> Unit) {
    var h by remember { mutableStateOf(start) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New habit" else "Edit habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(h.emoji, { v -> h = h.copy(emoji = v.take(4)) }, Modifier.width(72.dp), label = { Text("Icon") }, singleLine = true)
                    OutlinedTextField(h.name, { h = h.copy(name = it) }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                }
                Text("Days", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        val name = java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        FilterChip(d in h.days, { h = h.copy(days = if (d in h.days && h.days.size > 1) h.days - d else h.days + d) }, { Text(name) })
                    }
                }
                if (!isNew) TextButton({ confirm = true }) { Text("Delete habit", color = C.Bad) }
            }
        },
        confirmButton = { TextButton({ onSave(h.copy(name = h.name.trim(), emoji = h.emoji.ifBlank { "✅" })) }, enabled = h.name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete \"${start.name}\"?") }, text = { Text("Its whole history goes too. This can't be undone.") },
        confirmButton = { TextButton(onDelete) { Text("Delete", color = C.Bad) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}
