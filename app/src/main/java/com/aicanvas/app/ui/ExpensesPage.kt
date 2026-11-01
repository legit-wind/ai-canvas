package com.aicanvas.app.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.data.Expense
import com.aicanvas.app.data.ExpenseCategory
import com.aicanvas.app.data.Expenses
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The colour of an expense category (or income). */
fun categoryColor(vm: AppViewModel, name: String): Color =
    if (name == "Income") C.Good else Tones.ink(vm.expenseSettings.categories.firstOrNull { it.name == name }?.color) ?: C.Muted

fun categoryEmoji(vm: AppViewModel, name: String): String =
    if (name == "Income") "💰" else vm.expenseSettings.categories.firstOrNull { it.name == name }?.emoji ?: "•"

/** Monthly budget progress with an overspend warning; nothing when no budget is set. */
@Composable
fun BudgetBar(vm: AppViewModel, spent: Double) {
    val budget = vm.expenseSettings.budget
    val used = Expenses.budgetUsed(spent, budget) ?: return
    val cur = vm.expenseSettings.currency
    val color = when { used > 1 -> C.Bad; used > 0.8 -> Tones.ink("amber") ?: C.Bad; else -> C.Good }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator({ used.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = color, trackColor = C.Raised, drawStopIndicator = {}, gapSize = 0.dp)
        Text(
            if (used > 1) "⚠️ Over budget by $cur${Expenses.money(spent - budget)}" else "$cur${Expenses.money(budget - spent)} left of $cur${Expenses.money(budget)} this month",
            color = if (used > 1) C.Bad else C.Muted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun ExpenseLine(e: Expense, vm: AppViewModel, onClick: (() -> Unit)? = null) {
    val cur = vm.expenseSettings.currency
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(categoryColor(vm, e.category).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(categoryEmoji(vm, e.category), fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.note.ifBlank { e.category }, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(e.category, e.method, if (e.fromRepeat.isNotEmpty()) "recurring" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                color = C.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text((if (e.income) "+" else "−") + cur + Expenses.money(e.amount), color = if (e.income) C.Good else C.Text, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensesPage(vm: AppViewModel) {
    val today = LocalDate.now()
    val cur = vm.expenseSettings.currency
    var editing by remember { mutableStateOf<Expense?>(null) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var deleted by remember { mutableStateOf<Expense?>(null) }
    var showCats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(deleted) { if (deleted != null) { delay(6000); deleted = null } }
    val totals = Expenses.totals(vm.expenses, today)
    val byCat = Expenses.byCategory(vm.expenses, today)
    val trend = Expenses.trend(vm.expenses, today, 14)
    val list = Expenses.filter(vm.expenses, query, cat)
    val templates = vm.expenses.filter { it.repeat != "none" }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ editing = Expense(amount = 0.0, category = vm.expenseSettings.categories.first().name) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add expense")
                    }
                    IconButton({ showCats = true }) { Icon(Icons.Rounded.Category, "Categories", tint = C.Muted) }
                    IconButton({ showSettings = true }) { Icon(Icons.Rounded.Tune, "Currency and budget", tint = C.Muted) }
                    IconButton({
                        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_SUBJECT, "Expenses.csv").putExtra(Intent.EXTRA_TEXT, Expenses.csv(vm.expenses))
                        context.startActivity(Intent.createChooser(send, "Export expenses (CSV)"))
                    }, enabled = vm.expenses.isNotEmpty()) { Icon(Icons.Rounded.IosShare, "Export CSV", tint = C.Muted) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Today" to totals.today, "This week" to totals.week, "This month" to totals.month).forEach { (l, v) ->
                            Column(Modifier.weight(1f)) {
                                Text(l, color = C.Muted, style = MaterialTheme.typography.labelSmall)
                                Text("$cur${Expenses.money(v)}", color = C.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                    if (totals.monthIncome > 0) Text("Income this month: $cur${Expenses.money(totals.monthIncome)} · Net $cur${Expenses.money(totals.monthIncome - totals.month)}",
                        color = C.Good, style = MaterialTheme.typography.bodySmall)
                    BudgetBar(vm, totals.month)
                    if (vm.expenseSettings.budget <= 0) Text("Set a monthly budget", color = C.Accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { showSettings = true })
                }
            }
            if (byCat.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val sum = byCat.sumOf { it.second }
                    val colors = byCat.map { categoryColor(vm, it.first) }
                    val track = C.Raised
                    Canvas(Modifier.size(110.dp)) {
                        val stroke = 18.dp.toPx()
                        val d = size.minDimension - stroke
                        var start = -90f
                        drawArc(track, 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke))
                        byCat.forEachIndexed { i, (_, v) ->
                            val sweep = (v / sum * 360).toFloat()
                            drawArc(colors[i], start, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke))
                            start += sweep
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("This month by category", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                        byCat.take(6).forEachIndexed { i, (name, v) ->
                            Row(Modifier.clickable { cat = if (cat == name) null else name }, verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i])); Spacer(Modifier.width(6.dp))
                                Text("${categoryEmoji(vm, name)} $name", Modifier.weight(1f), color = C.Text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("${(v / sum * 100).toInt()}%", color = C.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (trend.any { it.second > 0 }) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val max = trend.maxOf { it.second }.coerceAtLeast(1.0)
                    Text("Last 14 days · avg $cur${Expenses.money(trend.sumOf { it.second } / 14)}/day", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                    val bar = C.Accent; val todayBar = C.Accent; val track = C.Raised
                    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                        val w = size.width / trend.size
                        trend.forEachIndexed { i, (_, v) ->
                            val h = (v / max * size.height).toFloat().coerceAtLeast(if (v > 0) 3f else 0f)
                            drawRoundRect(track, Offset(i * w + w * 0.2f, 0f), Size(w * 0.6f, size.height), CornerRadius(4f))
                            drawRoundRect(if (i == trend.lastIndex) todayBar else bar.copy(alpha = 0.55f), Offset(i * w + w * 0.2f, size.height - h), Size(w * 0.6f, h), CornerRadius(4f))
                        }
                    }
                    Row { Text(trend.first().first.format(DateTimeFormatter.ofPattern("d MMM")), color = C.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); Text("Today", color = C.Muted, style = MaterialTheme.typography.labelSmall) }
                }
            }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search notes, categories, amounts") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(cat == null, { cat = null }, { Text("All") })
                    (vm.expenseSettings.categories.map { it.name } + "Income").forEach { n ->
                        FilterChip(cat == n, { cat = if (cat == n) null else n }, { Text("${categoryEmoji(vm, n)} $n") })
                    }
                }
            }
            if (templates.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp)) {
                    Text("Recurring", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                    templates.forEach { t -> ExpenseLine(t.copy(note = "${t.note.ifBlank { t.category }} · ${t.repeat}"), vm) { editing = t } }
                }
            }
            if (list.isEmpty()) item { Text(if (vm.expenses.isEmpty()) "No expenses yet. Add one above, type \"lunch 120\" on the home card, or tell the AI \"spent 120 on lunch\"." else "Nothing matches.", color = C.Muted, modifier = Modifier.padding(top = 20.dp)) }
            list.groupBy { it.date }.forEach { (date, entries) ->
                item(key = "d$date") {
                    val d = LocalDate.parse(date)
                    val spent = entries.filter { !it.income }.sumOf { it.amount }
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM")) },
                            Modifier.weight(1f), color = C.Muted, style = MaterialTheme.typography.labelMedium)
                        if (spent > 0) Text("$cur${Expenses.money(spent)}", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                items(entries, key = { it.id }) { e -> ExpenseLine(e, vm) { editing = e } }
            }
        }
        deleted?.let { d ->
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Raised).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Deleted ${cur}${Expenses.money(d.amount)} ${d.note.ifBlank { d.category }}", Modifier.weight(1f), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton({ vm.restoreExpense(d); deleted = null }) { Text("Undo") }
            }
        }
    }
    editing?.let { start ->
        ExpenseEditor(vm, start, vm.expenses.none { it.id == start.id }, { editing = null }, { vm.saveExpense(it); editing = null },
            { deleted = vm.deleteExpense(start.id); editing = null })
    }
    if (showCats) CategoriesDialog(vm) { showCats = false }
    if (showSettings) ExpenseSettingsDialog(vm) { showSettings = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditor(vm: AppViewModel, start: Expense, isNew: Boolean, onDismiss: () -> Unit, onSave: (Expense) -> Unit, onDelete: () -> Unit) {
    var e by remember { mutableStateOf(start) }
    var amount by remember { mutableStateOf(if (start.amount > 0) Expenses.money(start.amount).replace(",", "") else "") }
    var pick by remember { mutableStateOf(false) }
    val value = amount.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New entry" else "Edit entry") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!e.income, { e = e.copy(income = false, category = if (e.category == "Income") vm.expenseSettings.categories.first().name else e.category) }, { Text("Expense") })
                    FilterChip(e.income, { e = e.copy(income = true, category = "Income") }, { Text("Income") })
                }
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), label = { Text("Amount") },
                    prefix = { Text(vm.expenseSettings.currency) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(e.note, { e = e.copy(note = it) }, Modifier.fillMaxWidth(), label = { Text("Note") }, singleLine = true)
                if (!e.income) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.expenseSettings.categories.forEach { c -> FilterChip(e.category == c.name, { e = e.copy(category = c.name) }, { Text("${c.emoji} ${c.name}") }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Expenses.METHODS.forEach { m -> FilterChip(e.method == m, { e = e.copy(method = m) }, { Text(m) }) }
                }
                OutlinedButton({ pick = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text(LocalDate.parse(e.date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))) }
                Text("Repeat", color = C.Muted, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Expenses.REPEATS.forEach { r -> FilterChip(e.repeat == r, { e = e.copy(repeat = r, lastCopied = if (r != start.repeat) "" else start.lastCopied) }, { Text(r.replaceFirstChar { it.uppercase() }) }) }
                }
                if (!isNew) TextButton(onDelete) { Icon(Icons.Rounded.DeleteOutline, null, tint = C.Bad); Spacer(Modifier.width(4.dp)); Text("Delete", color = C.Bad) }
            }
        },
        confirmButton = { TextButton({ onSave(e.copy(amount = value ?: 0.0, note = e.note.trim())) }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
    if (pick) {
        val state = rememberDatePickerState(LocalDate.parse(e.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog({ pick = false }, confirmButton = {
            TextButton({ state.selectedDateMillis?.let { e = e.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; pick = false }) { Text("OK") }
        }) { DatePicker(state) }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var cats by remember { mutableStateOf(vm.expenseSettings.categories) }
    var emoji by remember { mutableStateOf("🏷️") }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("blue") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(c.emoji, { v -> cats = cats.toMutableList().also { it[i] = c.copy(emoji = v.take(4)) } }, Modifier.width(64.dp), singleLine = true)
                        Spacer(Modifier.width(6.dp))
                        Text(c.name, Modifier.weight(1f), color = C.Text)
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Tones.ink(c.color) ?: C.Muted).clickable {
                            val all = Tones.ALL; cats = cats.toMutableList().also { it[i] = c.copy(color = all[(all.indexOf(c.color) + 1) % all.size]) }
                        })
                        IconButton({ cats = cats.filterIndexed { j, _ -> j != i } }, enabled = cats.size > 1) { Icon(Icons.Rounded.Close, "Remove ${c.name}", tint = C.Muted) }
                    }
                }
                HorizontalDivider(color = C.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, Modifier.width(64.dp), singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(name, { name = it }, Modifier.weight(1f), placeholder = { Text("New category") }, singleLine = true)
                    IconButton({
                        cats = cats + ExpenseCategory(name.trim(), emoji.ifBlank { "•" }, color); name = ""; color = Tones.ALL[(Tones.ALL.indexOf(color) + 1) % Tones.ALL.size]
                    }, enabled = name.isNotBlank() && cats.none { it.name.equals(name.trim(), true) } && !name.trim().equals("Income", true)) { Icon(Icons.Rounded.Add, "Add category", tint = C.Accent) }
                }
                Text("Tap a colour dot to change it. Entries in a removed category keep their label.", color = C.Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ vm.saveExpenseSettings(vm.expenseSettings.copy(categories = cats)); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseSettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var currency by remember { mutableStateOf(vm.expenseSettings.currency) }
    var budget by remember { mutableStateOf(if (vm.expenseSettings.budget > 0) Expenses.money(vm.expenseSettings.budget).replace(",", "") else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency & budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("₹", "$", "€", "£", "¥").forEach { c -> FilterChip(currency == c, { currency = c }, { Text(c) }) }
                }
                OutlinedTextField(currency, { currency = it.take(4) }, Modifier.fillMaxWidth(), label = { Text("Currency symbol") }, singleLine = true)
                OutlinedTextField(budget, { budget = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), label = { Text("Monthly budget (empty = none)") },
                    prefix = { Text(currency) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton({ vm.saveExpenseSettings(vm.expenseSettings.copy(currency = currency.ifBlank { "₹" }, budget = budget.toDoubleOrNull() ?: 0.0)); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
