package com.aicanvas.app.ui

import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aicanvas.app.AppViewModel
import com.aicanvas.app.BuildConfig
import com.aicanvas.app.ai.BaseUrls
import com.aicanvas.app.ai.cleanApiKey
import com.aicanvas.app.data.AppSettings
import com.aicanvas.app.data.PROVIDER_ANTHROPIC
import com.aicanvas.app.data.PROVIDER_OPENAI
import com.aicanvas.app.phone.Briefing
import kotlinx.coroutines.delay

private val CLAUDE_MODELS = listOf(
    "claude-haiku-5-5" to "Haiku 5.5 · cheapest",
    "claude-sonnet-5-5" to "Sonnet 5.5",
    "claude-opus-5-5" to "Opus 5.5 · priciest",
)

/** One entry on the Settings home: an icon in a soft coloured badge, a title and a one-line summary. */
private data class MenuEntry(val page: String, val title: String, val icon: ImageVector, val tone: String)

private val ENTRIES = listOf(
    MenuEntry("tasks", "Tasks", Icons.Rounded.CheckCircle, "green"),
    MenuEntry("habits", "Habits", Icons.Rounded.CheckCircle, "teal"),
    MenuEntry("countdowns", "Countdowns", Icons.Rounded.CalendarMonth, "amber"),
    MenuEntry("expenses", "Expenses", Icons.Rounded.AccountBalanceWallet, "green"),
    MenuEntry("homecards", "Home cards", Icons.Rounded.Dashboard, "blue"),
    MenuEntry("ai", "AI model & key", Icons.Rounded.AutoAwesome, "violet"),
    MenuEntry("appearance", "Appearance", Icons.Rounded.Palette, "rose"),
    MenuEntry("reminders", "Reminders & briefing", Icons.Rounded.NotificationsNone, "amber"),
    MenuEntry("calendar", "Calendar & permissions", Icons.Rounded.CalendarMonth, "blue"),
    MenuEntry("memories", "Memories", Icons.Rounded.Psychology, "teal"),
    MenuEntry("data", "Data & backup", Icons.Rounded.Storage, "green"),
    MenuEntry("activity", "AI activity", Icons.Rounded.Timeline, "violet"),
    MenuEntry("reset", "Reset", Icons.Rounded.RestartAlt, "rose"),
    MenuEntry("about", "About", Icons.Rounded.Info, "blue"),
)

/** The hamburger popup: every page in one list, with Settings as a single entry at the end. */
@Composable
fun QuickMenu(vm: AppViewModel, open: Boolean, onDismiss: () -> Unit) {
    androidx.compose.material3.DropdownMenu(open, onDismiss, shape = RoundedCornerShape(16.dp), containerColor = if (C.dark) C.Raised else C.Card) {
        val pages = listOf("tasks", "habits", "countdowns", "expenses", "reminders", "memories", "activity")
        pages.forEach { id ->
            val e = ENTRIES.first { it.page == id }
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(e.title, color = C.Text) },
                leadingIcon = { Icon(e.icon, null, tint = C.Muted) },
                onClick = { onDismiss(); vm.openPage(id) },
            )
        }
        HorizontalDivider(color = C.Line)
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Settings", color = C.Text) },
            leadingIcon = { Icon(Icons.Rounded.Settings, null, tint = C.Muted) },
            onClick = { onDismiss(); vm.openSettings() },
        )
    }
}

/** The full-screen menu: a Settings home with grouped entries, each opening its own page. Back returns home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(vm: AppViewModel) {
    val page = vm.menuPage.takeIf { p -> ENTRIES.any { it.page == p } } ?: "main"
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ENTRIES.firstOrNull { it.page == page }?.title ?: "Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (page != "main" && !vm.menuDirect) vm.menuPage = "main" else vm.closeMenu() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        AnimatedContent(
            page,
            Modifier.padding(pad),
            transitionSpec = {
                val dir = if (targetState != "main") 1 else -1
                (slideInHorizontally { it * dir / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 4 } + fadeOut())
            },
            label = "menu page",
        ) { p ->
            when (p) {
                "tasks" -> TasksPage(vm)
                "countdowns" -> CountdownsPage(vm)
                "expenses" -> ExpensesPage(vm)
                "habits" -> HabitsPage(vm)
                "memories" -> MemoriesPage(vm)
                "activity" -> ActivityPage(vm)
                "main" -> SettingsHome(vm)
                else -> SubPage { SettingsSection(vm, p) }
            }
        }
    }
}

@Composable
private fun SubPage(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(20.dp),
) { content(); Spacer(Modifier.height(24.dp)) }

private fun summary(vm: AppViewModel, page: String): String = when (page) {
    "ai" -> if (vm.settings.isConfigured) vm.settings.model else "Not set up yet: add a key"
    "appearance" -> "Theme: ${if (vm.themeMode == THEME_OLED) "Black" else vm.themeMode.replaceFirstChar { it.uppercase() }} · accent ${vm.accent} · text ${vm.textSize}"
    "reminders" -> "${vm.reminders.size} active · briefing ${Briefing.label(vm.briefingTime)}"
    "calendar" -> if (vm.useCalendar) "Calendar on" else "Calendar off · notifications"
    "tasks" -> vm.tasks.count { !it.done }.let { if (vm.tasks.isEmpty()) "Your to-do list" else "$it open · ${vm.tasks.size - it} done" }
    "habits" -> if (vm.habits.isEmpty()) "Streaks and history" else "${vm.habits.size} habits · ${vm.habits.count { java.time.LocalDate.now().toString() in it.done }} done today"
    "countdowns" -> com.aicanvas.app.data.Countdowns.sorted(vm.countdowns, java.time.LocalDate.now()).firstOrNull()?.let { "${it.title} " + com.aicanvas.app.data.Countdowns.label(com.aicanvas.app.data.Countdowns.daysLeft(it, java.time.LocalDate.now())) } ?: "Exams, trips, big days"
    "expenses" -> if (vm.expenses.isEmpty()) "Spending, budget, charts" else "${vm.expenseSettings.currency}${com.aicanvas.app.data.Expenses.money(com.aicanvas.app.data.Expenses.totals(vm.expenses, java.time.LocalDate.now()).month)} this month"
    "homecards" -> if (!vm.moduleCards) "Auto-add off" else com.aicanvas.app.data.Modules.KINDS.count { vm.moduleShown(it) }.let { "$it live cards on screen" }
    "memories" -> if (vm.memories.isEmpty()) "None yet" else "${vm.memories.size} saved facts"
    "data" -> "Export, tidy up, clear the screen"
    "reset" -> "Clear cards, canvases, all data or settings"
    "activity" -> if (vm.runs.isEmpty()) "How each request went" else "${vm.runs.size} runs this session"
    else -> "Version ${BuildConfig.VERSION_NAME} · privacy"
}

@Composable
private fun SettingsHome(vm: AppViewModel) {
    SubPage {
        listOf(listOf("tasks", "habits", "countdowns", "expenses", "homecards"), listOf("ai", "appearance"), listOf("reminders", "calendar"), listOf("memories", "data", "activity"), listOf("reset", "about")).forEach { group ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
                group.forEachIndexed { i, id ->
                    val e = ENTRIES.first { it.page == id }
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = C.Line.copy(alpha = 0.6f))
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.menuPage = id }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ink = Tones.ink(e.tone) ?: C.Accent
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(e.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyLarge, color = C.Text)
                            Text(summary(vm, id), style = MaterialTheme.typography.bodyMedium, color = C.Muted, maxLines = 1)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsSection(vm: AppViewModel, page: String) {
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    when (page) {
        "ai" -> Section("Model and key") { AiSettings(vm) }

        "appearance" -> Section("Look") {
            Label("Theme")
            val modes = listOf(THEME_SYSTEM to "System", THEME_LIGHT to "Light", THEME_DARK to "Dark", THEME_OLED to "Black")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                modes.forEachIndexed { i, (id, name) ->
                    SegmentedButton(
                        selected = vm.themeMode == id,
                        onClick = { vm.setTheme(id) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(name) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Label("Text size")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TEXT_SIZES.forEachIndexed { i, (id, _) ->
                    SegmentedButton(
                        selected = vm.textSize == id,
                        onClick = { vm.chooseTextSize(id) },
                        shape = SegmentedButtonDefaults.itemShape(i, TEXT_SIZES.size),
                    ) { Text(id.replaceFirstChar { it.uppercase() }) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Label("Accent colour")
            FlowRow(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ACCENTS.forEach { name ->
                    val color = if (name == "default") (if (C.dark) Color(0xFFA5B0FF) else Color(0xFF4F5BD5)) else paletteColor(name)
                    val chosen = vm.accent == name
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (chosen) 3.dp else 0.dp, if (chosen) C.Line else Color.Transparent, CircleShape)
                            .clickable(role = Role.RadioButton, onClickLabel = "Use $name") { vm.setAccentColor(name) }
                            .semantics { contentDescription = "$name accent"; selected = chosen },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chosen) Icon(Icons.Rounded.Check, null, tint = onColor(color), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        "reminders" -> {
            Section("Morning briefing") {
                NavRow("Briefing time", Briefing.label(vm.briefingTime)) { vm.cycleBriefing() }
                Text("A notification listing your most important cards. Made on the phone, no AI call.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            }
            Section("Active reminders") {
                if (vm.reminders.isEmpty()) Text("None. Ask the AI, e.g. \"remind me at 6pm to call mom\".", Modifier.padding(16.dp), color = C.Muted, style = MaterialTheme.typography.bodyMedium)
                vm.reminders.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    NavRow(r.text, java.time.Instant.ofEpochMilli(r.at).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")) + if (r.repeat.isNotBlank() && r.repeat != "none") " · ${r.repeat}" else "") {}
                }
            }
        }

        "calendar" -> {
            Section("Calendar") {
                SwitchRow("Use my calendar", "Upcoming events are sent to the AI with each message", vm.useCalendar) { vm.toggleCalendar() }
            }
            Section("Permissions") {
                val notifOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                val exactOn = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                    context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
                val calOn = com.aicanvas.app.phone.PhoneCalendar.granted(context)
                NavRow("Notifications · " + if (notifOn) "On ✓" else "Off ⚠️", if (notifOn) "Reminders, timers and the briefing can alert you" else "Reminders can't alert you. Tap to turn on", arrow = true) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                Divider()
                NavRow("Exact alarms · " + if (exactOn) "On ✓" else "Off ⚠️", if (exactOn) "Reminders ring on the minute" else "Reminders may arrive a few minutes late. Tap to allow", arrow = true) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)))
                    }
                }
                Divider()
                NavRow("Calendar · " + if (calOn) "Allowed ✓" else "Not allowed", "Only read when \"Use my calendar\" is on", arrow = true) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)))
                }
                Divider()
                NavRow("All app permissions", "Voice uses Google's speech screen, and photos use the picker, so no mic or camera permission is needed", arrow = true) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)))
                }
            }
        }

        "data" -> Section("Your data") {
            NavRow("Tidy up now", "AI re-sorts and re-sizes your cards") { vm.tidyNow(); vm.closeMenu() }
            Divider()
            NavRow("Auto-organize into canvases", "Sort cards into Academic, Personal, Health, Work, Finance, News & Info — preview first") { vm.planOrganize(); vm.closeMenu() }
            Divider()
            SwitchRow("Auto-place new cards", "Put each new card on the canvas matching its category", vm.autoPlace) { vm.toggleAutoPlace() }
            Divider()
            NavRow("Refresh live cards", "Weather, news, rates and daily cards get fresh content now") { vm.refreshLive(force = true); vm.closeMenu() }
            Divider()
            NavRow("Export", "Share your cards, memories and reminders as JSON") {
                val send = Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_SUBJECT, "AI Canvas export")
                    .putExtra(Intent.EXTRA_TEXT, vm.exportJson())
                context.startActivity(Intent.createChooser(send, "Export"))
            }
            Divider()
            NavRow("Clear the screen", "Removes every card", color = C.Bad) { confirmClear = true }
        }

        "reset" -> ResetSection(vm)
        "homecards" -> Section("Live home cards") {
            SwitchRow("Add cards automatically", "A live card appears on Home when you add your first task, habit, countdown or expense", vm.moduleCards) { vm.toggleModuleCards() }
            com.aicanvas.app.data.Modules.KINDS.forEach { k ->
                Divider()
                val shown = vm.moduleShown(k)
                NavRow("Show on home: ${com.aicanvas.app.data.Modules.title(k)}", if (shown) "On screen" else if (k in vm.hiddenModules) "Removed · tap to restore" else "Tap to add", arrow = !shown) {
                    if (!shown) vm.showModule(k)
                }
            }
        }

        else -> Section("About") {
            NavRow("AI Canvas", "Version ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · debug" else ""}") {}
            Divider()
            Text(
                "Your cards, memories and reminders stay on this phone. Only what you ask, plus the cards on screen and your most relevant memories, is sent to the AI provider you choose.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = C.Muted,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the screen?") },
            text = { Text("All cards and their reminders are removed. Memories are kept.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearAll(); vm.closeMenu() }) { Text("Clear", color = C.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            containerColor = C.Card,
        )
    }
}

@Composable
private fun AiSettings(vm: AppViewModel) {
    val cur = vm.settings
    var provider by remember { mutableStateOf(cur.provider) }
    var apiKey by remember { mutableStateOf(cur.apiKey) }
    var model by remember { mutableStateOf(cur.model) }
    var baseUrl by remember { mutableStateOf(cur.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(2000); saved = false } }

    val draft = AppSettings(provider, cleanApiKey(apiKey), model.trim(), if (provider == PROVIDER_ANTHROPIC) baseUrl.trim() else com.aicanvas.app.ai.Gemini.normalize(baseUrl))
    val urlProblem = if (provider == PROVIDER_ANTHROPIC) null else BaseUrls.problem(baseUrl)
    val complete = draft.apiKey.isNotBlank() && draft.model.isNotBlank() && urlProblem == null
    val changed = draft != cur

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!cur.isConfigured) {
            Text(
                "Add an API key to start. For Claude, create one at console.anthropic.com → API keys.",
                style = MaterialTheme.typography.bodyMedium, color = C.Muted,
            )
        }
        Label("Provider", inset = false)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(provider == PROVIDER_ANTHROPIC, {
                provider = PROVIDER_ANTHROPIC
                if (!model.startsWith("claude")) model = CLAUDE_MODELS[0].first
                vm.clearTestResult()
            }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Claude") }
            SegmentedButton(provider == PROVIDER_OPENAI, {
                provider = PROVIDER_OPENAI
                if (model.startsWith("claude")) model = ""
                vm.clearTestResult()
            }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("OpenAI-compatible") }
        }

        OutlinedTextField(
            apiKey, { apiKey = it; vm.clearTestResult() },
            Modifier.fillMaxWidth(),
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showKey) "Hide key" else "Show key")
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )

        if (provider == PROVIDER_ANTHROPIC) {
            Label("Model", inset = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CLAUDE_MODELS.forEach { (id, name) ->
                    FilterChip(model == id, { model = id; vm.clearTestResult() }, label = { Text(name) })
                }
            }
        } else {
            OutlinedTextField(
                baseUrl, { baseUrl = it; vm.clearTestResult() },
                Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                isError = urlProblem != null && baseUrl.isNotBlank(),
                supportingText = (urlProblem ?: com.aicanvas.app.ai.Gemini.hint(baseUrl))?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
            )
            OutlinedTextField(
                model, { model = it; vm.clearTestResult() },
                Modifier.fillMaxWidth(),
                label = { Text("Model") },
                placeholder = { Text("e.g. gpt-4o-mini or llama3.1") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
            )
            if (com.aicanvas.app.ai.OpenRouter.isOpenRouter(baseUrl)) {
                val free = vm.freeModels
                if (free == null) {
                    TextButton(onClick = { vm.loadFreeModels() }, enabled = !vm.loadingModels) {
                        Text(if (vm.loadingModels) "Loading free models…" else "Show free models")
                    }
                } else {
                    Label("Free models (${free.size})", inset = false)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        free.take(24).forEach { m ->
                            FilterChip(model == m.id, { model = m.id; vm.clearTestResult() }, label = { Text(m.name.removeSuffix(" (free)")) })
                        }
                    }
                    Text("Free models can be slow or busy. Tap Test connection after picking one.", style = MaterialTheme.typography.bodySmall, color = C.Muted)
                }
            } else if (baseUrl.isBlank()) {
                TextButton(onClick = { baseUrl = com.aicanvas.app.ai.OpenRouter.BASE_URL; vm.clearTestResult() }) { Text("Use OpenRouter (has free models)") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.testConnection(draft) }, enabled = complete && !vm.testing, modifier = Modifier.weight(1f)) {
                if (vm.testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (vm.testing) "Testing…" else "Test connection")
            }
            Button(
                onClick = { vm.saveSettings(draft); saved = true },
                // Saving never depends on a passing test.
                enabled = complete && (changed || !saved),
                modifier = Modifier.weight(1f),
            ) { Text(if (saved) "Saved" else "Save") }
        }
        vm.testResult?.let { (ok, message) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background((if (ok) C.Good else C.Bad).copy(alpha = 0.12f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = if (ok) C.Good else C.Bad, modifier = Modifier.size(18.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = C.Text)
            }
        }
    }
}

/** What the AI remembers about you. It adds these itself; you can remove any. */
@Composable
private fun MemoriesPage(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Things the AI has learned about you. It uses them to personalise your screen.", style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        if (vm.memories.isEmpty()) {
            Text("Nothing yet. Try telling it your name, your college or a goal.", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyLarge, color = C.Muted)
        }
        vm.memories.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = C.Text)
                IconButton(onClick = { vm.forget(m.id) }) { Icon(Icons.Rounded.Close, "Forget: ${m.text}", tint = C.Muted) }
            }
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.labelMedium,
            color = C.Muted,
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Card).padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Label(text: String, inset: Boolean = true) {
    Text(
        text,
        Modifier.padding(start = if (inset) 16.dp else 0.dp, top = if (inset) 12.dp else 0.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = C.Text,
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = C.Line.copy(alpha = 0.6f))

@Composable
private fun NavRow(title: String, value: String? = null, color: Color = C.Text, arrow: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
        if (arrow) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Muted)
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch, onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = C.Text)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = C.Raised,
    unfocusedContainerColor = C.Raised,
    unfocusedBorderColor = C.Line,
)

/** Settings → Reset: each option asks first, in plain words, what will be lost. */
@Composable
private fun ColumnScope.ResetSection(vm: AppViewModel) {
    var ask by remember { mutableStateOf<String?>(null) }
    var keepKey by remember { mutableStateOf(true) }
    var typed by remember { mutableStateOf("") }
    Section("Reset") {
        NavRow("Reset settings to defaults", "Theme, accent, text size, briefing, calendar and auto-place. Your cards and AI key stay") { ask = "settings" }
        Divider()
        NavRow("Clear all cards", "Removes every card on every canvas (undo right after)", color = C.Bad) { ask = "cards" }
        Divider()
        NavRow("Reset canvases", "Back to one empty Home canvas: all canvases and cards go (undo right after)", color = C.Bad) { ask = "canvases" }
        Divider()
        NavRow("Reset all app data", "Cards, canvases, memories, reminders, tasks, habits, countdowns and settings. Can't be undone", color = C.Bad) { ask = "all" }
    }
    val a = ask ?: return
    val (title, body) = when (a) {
        "settings" -> "Reset settings?" to "Appearance, briefing, calendar and auto-place go back to defaults. Cards and your AI key stay."
        "cards" -> "Clear all cards?" to "Every card on every canvas is removed. You can undo straight after."
        "canvases" -> "Reset canvases?" to "All canvases and all their cards are removed, leaving one empty Home. You can undo straight after."
        else -> "Reset all app data?" to "Everything on this phone for AI Canvas is erased. This can't be undone. Type RESET to confirm."
    }
    val strong = a == "all"
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = null; typed = "" },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                if (strong) {
                    androidx.compose.material3.OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("Type RESET") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(keepKey, { keepKey = it })
                        Text("Keep my AI key and model")
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                {
                    when (a) {
                        "settings" -> vm.resetSettings()
                        "cards" -> vm.clearAll()
                        "canvases" -> vm.resetCanvases()
                        else -> vm.resetAllData(keepKey)
                    }
                    ask = null; typed = ""
                    if (a != "settings") vm.closeMenu()
                },
                enabled = !strong || typed.trim() == "RESET",
            ) { Text(if (a == "settings") "Reset" else "Erase", color = C.Bad) }
        },
        dismissButton = { androidx.compose.material3.TextButton({ ask = null; typed = "" }) { Text("Cancel") } },
    )
}
