package com.aicanvas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.Countdowns
import com.aicanvas.app.data.Expenses
import com.aicanvas.app.data.Habit
import com.aicanvas.app.data.Habits
import com.aicanvas.app.data.Modules
import com.aicanvas.app.data.TaskItem
import com.aicanvas.app.data.Tasks
import java.time.LocalDate

/** A live home card for one of the app's own lists. It reads the view model directly, so both sides stay in sync. */
@Composable
fun ModuleCard(kind: String, vm: AppViewModel) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tapping the header opens the full page.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.openPage(kind) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Modules.title(kind), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = C.Text)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Open ${Modules.title(kind)}", tint = C.Muted)
        }
        when (kind) {
            "tasks" -> TasksBody(vm, today)
            "habits" -> HabitsBody(vm, today)
            "countdowns" -> CountdownsBody(vm, today)
            else -> ExpensesBody(vm, today)
        }
    }
}

@Composable
private fun QuickAdd(hint: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val send = { text.trim().takeIf { it.isNotEmpty() }?.let(onAdd); text = "" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(hint, color = C.Muted) }, singleLine = true,
            shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { send() }))
        IconButton(send, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, "Add", tint = C.Accent) }
    }
}

@Composable
private fun Tick(done: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(if (done) C.Accent else Color.Transparent)
            .then(if (done) Modifier else Modifier.background(C.Raised)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Icons.Rounded.Check, "Done", tint = C.Bg, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun TasksBody(vm: AppViewModel, today: LocalDate) {
    val list = Tasks.view(vm.tasks, "today", "", today)
    val doneToday = vm.tasks.filter { it.done && it.doneAt?.let { t -> java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today } == true }
    if (list.isEmpty() && doneToday.isEmpty()) Text("Nothing due today.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    (list + doneToday).distinctBy { it.id }.take(6).forEach { t -> TaskLine(t) { vm.toggleTask(t.id) } }
    val more = list.size - 6
    if (more > 0) Text("+$more more", color = C.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { vm.openPage("tasks") })
    QuickAdd("Add a task for today") { vm.saveTask(TaskItem(title = it, due = today.toString())) }
}

@Composable
private fun TaskLine(t: TaskItem, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Tick(t.done, onToggle)
        Spacer(Modifier.width(10.dp))
        Text(t.title, Modifier.weight(1f), color = if (t.done) C.Muted else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (t.done) TextDecoration.LineThrough else null)
        t.due?.let { d -> if (d < today()) Text("overdue", color = C.Bad, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun today() = LocalDate.now().toString()

@Composable
private fun HabitsBody(vm: AppViewModel, today: LocalDate) {
    val due = vm.habits.filter { Habits.isDue(it, today) }
    if (due.isEmpty()) Text(if (vm.habits.isEmpty()) "No habits yet." else "No habits due today.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    due.take(8).forEach { h -> HabitLine(h, today) { vm.saveHabit(Habits.toggle(h, today)) } }
    val n = due.count { today.toString() in it.done }
    if (due.isNotEmpty()) Text("$n of ${due.size} done today", color = C.Muted, style = MaterialTheme.typography.bodySmall)
    QuickAdd("New daily habit") { vm.saveHabit(Habit(name = it)) }
}

@Composable
private fun HabitLine(h: Habit, today: LocalDate, onToggle: () -> Unit) {
    val done = today.toString() in h.done
    Row(verticalAlignment = Alignment.CenterVertically) {
        Tick(done, onToggle)
        Spacer(Modifier.width(10.dp))
        Text(h.emoji, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(h.name, Modifier.weight(1f), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val s = Habits.streak(h, today)
        if (s > 0) Text("🔥 $s", color = C.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CountdownsBody(vm: AppViewModel, today: LocalDate) {
    val list = Countdowns.sorted(vm.countdowns, today).filter { Countdowns.daysLeft(it, today) >= 0 }
    if (list.isEmpty()) Text("No upcoming countdowns.", color = C.Muted, style = MaterialTheme.typography.bodyMedium)
    list.take(5).forEach { c ->
        val d = Countdowns.daysLeft(c, today)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 18.sp); Spacer(Modifier.width(10.dp))
            Text(c.title, Modifier.weight(1f), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (d > 1) "$d days" else Countdowns.label(d), color = C.Accent, fontWeight = FontWeight.SemiBold)
        }
    }
    OutlinedButton({ vm.openPage("countdowns") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add countdown")
    }
}

@Composable
private fun ExpensesBody(vm: AppViewModel, today: LocalDate) {
    val cur = vm.expenseSettings.currency
    val t = Expenses.totals(vm.expenses, today)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Today" to t.today, "Week" to t.week, "Month" to t.month).forEach { (l, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.Raised).padding(10.dp)) {
                Text(l, color = C.Muted, style = MaterialTheme.typography.labelSmall)
                Text("$cur${Expenses.money(v)}", color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
    BudgetBar(vm, t.month)
    Expenses.filter(vm.expenses, "", null).take(3).forEach { e -> ExpenseLine(e, vm) }
    QuickAdd("e.g. lunch 120") { s ->
        Expenses.parseQuick(s, vm.expenseSettings.categories)?.let(vm::saveExpense) ?: vm.openPage("expenses")
    }
}
