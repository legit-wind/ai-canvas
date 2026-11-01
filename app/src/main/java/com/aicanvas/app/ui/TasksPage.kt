package com.aicanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.Subtask
import com.aicanvas.app.data.TaskItem
import com.aicanvas.app.data.Tasks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val VIEW_NAMES = mapOf("all" to "Open", "today" to "Today", "upcoming" to "Upcoming", "priority" to "By priority", "completed" to "Done")
private val PRIORITY = mapOf(1 to "High", 2 to "Normal", 3 to "Low")

/** Settings → Tasks: a real, saved to-do list with views, search, subtasks and repeating tasks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksPage(vm: AppViewModel) {
    var view by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    val today = LocalDate.now()
    val shown = Tasks.view(vm.tasks, view, query, today)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search tasks, notes, tags") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
            shape = RoundedCornerShape(14.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tasks.VIEWS.forEach { v -> FilterChip(view == v, { view = v }, label = { Text(VIEW_NAMES[v]!!) }) }
        }
        Button({ editing = TaskItem(title = "") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add task")
        }
        Spacer(Modifier.height(10.dp))
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        query.isNotBlank() -> "No tasks match \"$query\"."
                        vm.tasks.isEmpty() -> "No tasks yet. Add one to get started."
                        view == "completed" -> "Nothing finished yet."
                        else -> "Nothing here right now."
                    },
                    color = C.Muted,
                )
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.id }) { t -> TaskRow(t, today, { vm.toggleTask(t.id) }) { editing = t } }
        }
    }
    editing?.let { t ->
        TaskEditor(t, isNew = vm.tasks.none { it.id == t.id }, onDismiss = { editing = null },
            onSave = { vm.saveTask(it); editing = null }, onDelete = { vm.deleteTask(t.id); editing = null })
    }
}

@Composable
private fun TaskRow(t: TaskItem, today: LocalDate, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).clickable(onClick = onOpen).padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(t.done, { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(t.title, color = if (t.done) C.Muted else C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) TextDecoration.LineThrough else null, style = MaterialTheme.typography.bodyLarge)
            val bits = buildList {
                Tasks.parseDate(t.due)?.let { d ->
                    add(when { d == today -> "Today"; d == today.plusDays(1) -> "Tomorrow"; else -> d.format(DateTimeFormatter.ofPattern("d MMM")) } +
                        if (Tasks.isOverdue(t, today)) " · overdue" else "")
                }
                if (t.priority != 2) add(PRIORITY[t.priority]!!)
                if (t.category.isNotBlank()) add(t.category)
                Tasks.progress(t).let { (d, n) -> if (n > 0) add("$d/$n steps") }
                if (t.repeat != "none") add("repeats ${t.repeat}")
                t.tags.forEach { add("#$it") }
            }
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = if (Tasks.isOverdue(t, today)) C.Bad else C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.priority == 1 && !t.done) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Bad))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(start: TaskItem, isNew: Boolean, onDismiss: () -> Unit, onSave: (TaskItem) -> Unit, onDelete: () -> Unit) {
    var t by remember { mutableStateOf(start) }
    var tags by remember { mutableStateOf(start.tags.joinToString(", ")) }
    var newStep by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New task" else "Edit task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(t.title, { t = t.copy(title = it) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(t.notes, { t = t.copy(notes = it) }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
                Text("Due", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val today = LocalDate.now()
                    FilterChip(t.due == today.toString(), { t = t.copy(due = today.toString()) }, { Text("Today") })
                    FilterChip(t.due == today.plusDays(1).toString(), { t = t.copy(due = today.plusDays(1).toString()) }, { Text("Tomorrow") })
                    AssistChip({ pickDate = true }, { Text(Tasks.parseDate(t.due)?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Pick date") })
                    if (t.due != null) AssistChip({ t = t.copy(due = null, repeat = "none") }, { Text("No date") })
                }
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(1, 2, 3).forEachIndexed { i, p ->
                        SegmentedButton(t.priority == p, { t = t.copy(priority = p) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(PRIORITY[p]!!) }
                    }
                }
                if (t.due != null) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tasks.REPEATS.forEach { r -> FilterChip(t.repeat == r, { t = t.copy(repeat = r) }, { Text(r.replaceFirstChar { it.uppercase() }) }) }
                    }
                }
                OutlinedTextField(t.category, { t = t.copy(category = it) }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
                OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("Tags (comma separated)") }, singleLine = true)
                Text("Steps", style = MaterialTheme.typography.labelLarge)
                t.subtasks.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.done, { t = t.copy(subtasks = t.subtasks.toMutableList().also { l -> l[i] = s.copy(done = it) }) })
                        Text(s.title, Modifier.weight(1f))
                        IconButton({ t = t.copy(subtasks = t.subtasks.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, "Remove step") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newStep, { newStep = it }, Modifier.weight(1f), placeholder = { Text("Add a step") }, singleLine = true)
                    IconButton({ if (newStep.isNotBlank()) { t = t.copy(subtasks = t.subtasks + Subtask(newStep.trim())); newStep = "" } },
                        enabled = newStep.isNotBlank()) { Icon(Icons.Rounded.Add, "Add step") }
                }
                if (!isNew) TextButton({ confirmDelete = true }) { Text("Delete task", color = C.Bad) }
            }
        },
        confirmButton = {
            TextButton({ onSave(t.copy(title = t.title.trim(), tags = Tasks.parseTags(tags))) }, enabled = t.title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(Tasks.parseDate(t.due)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { t = t.copy(due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                pickDate = false
            }) { Text("OK") }
        }) { DatePicker(state) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this task?") }, text = { Text("\"${start.title}\" will be removed. This can't be undone.") },
        confirmButton = { TextButton(onDelete) { Text("Delete", color = C.Bad) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
