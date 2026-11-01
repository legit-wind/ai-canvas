package com.aicanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.Countdown
import com.aicanvas.app.data.Countdowns
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Settings → Countdowns: days left until exams and other dates you add. */
@Composable
fun CountdownsPage(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Countdown?>(null) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Button({ editing = Countdown(title = "", date = today.plusDays(7).toString()) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add countdown")
        }
        Spacer(Modifier.height(10.dp))
        if (vm.countdowns.isEmpty()) Text("No countdowns yet. Add an exam or a big day to see how long is left.", color = C.Muted, modifier = Modifier.padding(top = 32.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(Countdowns.sorted(vm.countdowns, today), key = { it.id }) { c ->
                val days = Countdowns.daysLeft(c, today)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).clickable { editing = c }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium, color = if (days < 0) C.Muted else C.Text)
                        Text(LocalDate.parse(c.date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), style = MaterialTheme.typography.bodySmall, color = C.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (days > 1) {
                            Text("$days", style = MaterialTheme.typography.headlineSmall, color = C.Accent)
                            Text("days left", style = MaterialTheme.typography.labelSmall, color = C.Muted)
                        } else Text(Countdowns.label(days), color = if (days >= 0) C.Accent else C.Muted)
                    }
                }
            }
        }
    }
    editing?.let { start -> CountdownEditor(start, vm.countdowns.none { it.id == start.id }, { editing = null },
        { vm.saveCountdown(it); editing = null }, { vm.deleteCountdown(start.id); editing = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownEditor(start: Countdown, isNew: Boolean, onDismiss: () -> Unit, onSave: (Countdown) -> Unit, onDelete: () -> Unit) {
    var c by remember { mutableStateOf(start) }
    var pick by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New countdown" else "Edit countdown") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(c.emoji, { c = c.copy(emoji = it.take(4)) }, Modifier.width(72.dp), label = { Text("Icon") }, singleLine = true)
                    OutlinedTextField(c.title, { c = c.copy(title = it) }, Modifier.weight(1f), label = { Text("What") }, singleLine = true)
                }
                OutlinedButton({ pick = true }, Modifier.fillMaxWidth()) { Text(LocalDate.parse(c.date).format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
                if (!isNew) TextButton({ confirm = true }) { Text("Delete countdown", color = C.Bad) }
            }
        },
        confirmButton = { TextButton({ onSave(c.copy(title = c.title.trim())) }, enabled = c.title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
    if (pick) {
        val state = rememberDatePickerState(LocalDate.parse(c.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog({ pick = false }, confirmButton = {
            TextButton({ state.selectedDateMillis?.let { c = c.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; pick = false }) { Text("OK") }
        }) { DatePicker(state) }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Delete \"${start.title}\"?") },
        confirmButton = { TextButton(onDelete) { Text("Delete", color = C.Bad) } }, dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}
