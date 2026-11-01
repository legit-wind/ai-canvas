package com.aicanvas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicanvas.app.ai.Ai
import com.aicanvas.app.ai.Reply
import com.aicanvas.app.ai.Snapshot
import com.aicanvas.app.ai.sizeOf
import com.aicanvas.app.data.AppSettings
import com.aicanvas.app.data.Element
import com.aicanvas.app.data.Memory
import com.aicanvas.app.data.Reminder
import com.aicanvas.app.data.Store
import com.aicanvas.app.data.UiSchema
import com.aicanvas.app.data.byImportance
import com.aicanvas.app.phone.Network
import com.aicanvas.app.phone.Briefing
import com.aicanvas.app.phone.HomeWidget
import com.aicanvas.app.phone.Lookup
import com.aicanvas.app.phone.PhoneCalendar
import com.aicanvas.app.phone.Reminders
import com.aicanvas.app.phone.Timers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    /** One file write at a time, in order, off the main thread.
     *  Declared first: init code below saves files, and a later-declared val would still be null then. [write] returns false when it failed. */
    private val writer = Dispatchers.IO.limitedParallelism(1)

    var settings by mutableStateOf(store.loadSettings())
        private set
    /** Everything on the home screen, in screen order: most important first. */
    /** The canvases (screens of cards), Home first. */
    val canvases = mutableStateListOf<com.aicanvas.app.data.CanvasInfo>().apply { addAll(com.aicanvas.app.data.Canvases.parse(store.canvasesJson)) }
    /** The canvas on screen. */
    var currentCanvas by mutableStateOf(store.currentCanvas.takeIf { id -> canvases.any { it.id == id } } ?: com.aicanvas.app.data.Canvases.HOME_ID)
        private set
    var autoPlace by mutableStateOf(store.autoPlace)
        private set
    /** Every card on every canvas, in screen order: most important first. Cards from before canvases go to Home. */
    val elements = mutableStateListOf<Element>().apply { addAll(com.aicanvas.app.data.Canvases.migrate(store.loadElements(), canvases).sortedWith(byImportance)) }
    /** The cards on the current canvas. */
    val visibleElements: List<Element> get() = elements.filter { it.canvas == currentCanvas }
    val memories = mutableStateListOf<Memory>().apply { addAll(store.loadMemories()) }
    val reminders = mutableStateListOf<Reminder>().apply { addAll(store.loadReminders()) }

    var chatOpen by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    /** Which page of the menu is showing: "main", "settings" or "memories". */
    var menuPage by mutableStateOf("main")

    var draft by mutableStateOf("")
    /** Photos waiting to go with the next message (up to [com.aicanvas.app.data.Limits.MAX_PHOTOS]): preview and JPEG. */
    class Attached(val preview: Bitmap, val jpeg: ByteArray)
    val photos = androidx.compose.runtime.mutableStateListOf<Attached>()
    /** The last few AI runs of this session, newest first, for the Activity screen. Never saved. */
    val runs = androidx.compose.runtime.mutableStateListOf<com.aicanvas.app.ai.AiRun>()

    var busy by mutableStateOf(false)
        private set
    /** What the AI is doing right now, shown while it works. */
    var status by mutableStateOf("")
        private set
    /** Appearance settings, applied app-wide by AICanvasTheme. */
    var themeMode by mutableStateOf(store.theme)
        private set
    var accent by mutableStateOf(store.accent)
        private set

    fun setTheme(mode: String) { themeMode = mode; store.theme = mode }
    /** Tasks page (tasks.json). Changes save straight away. */
    val tasks = androidx.compose.runtime.mutableStateListOf<com.aicanvas.app.data.TaskItem>().apply { addAll(store.loadTasks()) }
    fun saveTask(t: com.aicanvas.app.data.TaskItem) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) tasks[i] = t else tasks.add(t)
        store.saveTasks(tasks.toList())
        if (i < 0) ensureModuleCards()
    }
    fun deleteTask(id: String) { tasks.removeAll { it.id == id }; store.saveTasks(tasks.toList()) }
    fun toggleTask(id: String) { tasks.firstOrNull { it.id == id }?.let { saveTask(com.aicanvas.app.data.Tasks.complete(it)) } }

    /** Habits page (habits.json). */
    val habits = androidx.compose.runtime.mutableStateListOf<com.aicanvas.app.data.Habit>().apply { addAll(store.loadHabits()) }
    fun saveHabit(h: com.aicanvas.app.data.Habit) {
        val i = habits.indexOfFirst { it.id == h.id }
        if (i >= 0) habits[i] = h else habits.add(h)
        store.saveHabits(habits.toList())
        if (i < 0) ensureModuleCards()
    }
    fun deleteHabit(id: String) { habits.removeAll { it.id == id }; store.saveHabits(habits.toList()) }

    /** Countdowns page (countdowns.json). */
    val countdowns = androidx.compose.runtime.mutableStateListOf<com.aicanvas.app.data.Countdown>().apply { addAll(store.loadCountdowns()) }
    fun saveCountdown(c: com.aicanvas.app.data.Countdown) {
        val i = countdowns.indexOfFirst { it.id == c.id }
        if (i >= 0) countdowns[i] = c else countdowns.add(c)
        store.saveCountdowns(countdowns.toList())
        if (i < 0) ensureModuleCards()
    }
    fun deleteCountdown(id: String) { countdowns.removeAll { it.id == id }; store.saveCountdowns(countdowns.toList()) }

    // ---------------- expenses ----------------
    /** Expenses page (expenses.json): entries plus recurring templates. */
    val expenses = mutableStateListOf<com.aicanvas.app.data.Expense>().apply {
        val saved = runCatching { store.loadExpenses() }.getOrDefault(emptyList())
        addAll(runCatching { com.aicanvas.app.data.Expenses.materialize(saved, LocalDate.now()) }.getOrDefault(saved))
    }
    var expenseSettings by mutableStateOf(store.expenseSettings)
        private set
    fun saveExpenseSettings(s: com.aicanvas.app.data.ExpenseSettings) { expenseSettings = s; store.expenseSettings = s }
    fun saveExpense(e: com.aicanvas.app.data.Expense) {
        val i = expenses.indexOfFirst { it.id == e.id }
        if (i >= 0) expenses[i] = e else expenses.add(e)
        // A new recurring entry starts today: its first copy is made at once.
        if (e.repeat != "none") { val m = com.aicanvas.app.data.Expenses.materialize(expenses.toList(), LocalDate.now()); expenses.clear(); expenses.addAll(m) }
        store.saveExpenses(expenses.toList())
        if (i < 0) ensureModuleCards()
    }
    /** Deletes an entry and returns it, so the page can offer Undo. */
    fun deleteExpense(id: String): com.aicanvas.app.data.Expense? {
        val e = expenses.firstOrNull { it.id == id } ?: return null
        expenses.removeAll { it.id == id }; store.saveExpenses(expenses.toList())
        return e
    }
    fun restoreExpense(e: com.aicanvas.app.data.Expense) { if (expenses.none { it.id == e.id }) { expenses.add(e); store.saveExpenses(expenses.toList()) } }

    // ---------------- live module cards ----------------
    var moduleCards by mutableStateOf(store.moduleCards)
        private set
    var hiddenModules by mutableStateOf(store.hiddenModules)
        private set
    fun toggleModuleCards() { moduleCards = !moduleCards; store.moduleCards = moduleCards; ensureModuleCards() }

    private fun hasModuleData() = mapOf(
        "tasks" to tasks.isNotEmpty(), "habits" to habits.isNotEmpty(),
        "countdowns" to countdowns.isNotEmpty(), "expenses" to expenses.isNotEmpty(),
    )

    /** Adds a live home card for each kind that has data but no card (unless turned off or removed by the user). */
    fun ensureModuleCards() {
        val missing = com.aicanvas.app.data.Modules.missing(hasModuleData(), elements.map { it.ui }, hiddenModules, moduleCards)
        if (missing.isEmpty()) return
        val top = (elements.maxOfOrNull { it.importance } ?: 50.0)
        missing.forEach { kind ->
            elements.add(Element(size = "full", ui = com.aicanvas.app.data.Modules.ui(kind), importance = minOf(100.0, top + 1), canvas = com.aicanvas.app.data.Canvases.HOME_ID))
        }
        elements.sortWith(byImportance)
        saveElements()
    }

    /** Puts a removed module card back on the home canvas (even with no data yet). */
    fun showModule(kind: String) {
        hiddenModules = hiddenModules - kind; store.hiddenModules = hiddenModules
        if (elements.none { com.aicanvas.app.data.Modules.kindOf(it.ui) == kind }) {
            val top = (elements.maxOfOrNull { it.importance } ?: 50.0)
            elements.add(Element(size = "full", ui = com.aicanvas.app.data.Modules.ui(kind), importance = minOf(100.0, top + 1), canvas = currentCanvas))
            elements.sortWith(byImportance); saveElements()
        }
    }
    fun moduleShown(kind: String) = elements.any { com.aicanvas.app.data.Modules.kindOf(it.ui) == kind }

    private fun hideModuleOf(e: Element) {
        com.aicanvas.app.data.Modules.kindOf(e.ui)?.let { hiddenModules = hiddenModules + it; store.hiddenModules = hiddenModules }
    }

    /** Moves a card to another canvas (undoable). */
    fun moveToCanvas(id: String, canvasId: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0 || canvases.none { it.id == canvasId } || elements[i].canvas == canvasId) return
        val before = elements.toList()
        elements[i] = elements[i].copy(canvas = canvasId)
        undo = Undo("Moved", before)
        saveElements()
    }

    /** Items the AI added to the app's own lists. Returns a short note like "1 task, 1 expense". */
    private fun applyAdds(add: Map<String, List<JSONObject>>) {
        add["tasks"]?.forEach { o -> com.aicanvas.app.data.TaskItem.fromJson(JSONObject(o.toString()).apply { remove("id") })?.let(::saveTask) }
        add["habits"]?.forEach { o -> com.aicanvas.app.data.Habit.fromJson(JSONObject(o.toString()).apply { remove("id"); remove("done") })?.let { h -> if (habits.none { it.name.equals(h.name, true) }) saveHabit(h) } }
        add["countdowns"]?.forEach { o -> com.aicanvas.app.data.Countdown.fromJson(JSONObject(o.toString()).apply { remove("id") })?.let(::saveCountdown) }
        add["expenses"]?.forEach { o ->
            val e = if (o.has("amount")) com.aicanvas.app.data.Expense.fromJson(JSONObject(o.toString()).apply { remove("id"); remove("createdAt") })
                else com.aicanvas.app.data.Expenses.parseQuick(o.optString("text"), expenseSettings.categories)
            e?.let { x ->
                val cat = expenseSettings.categories.firstOrNull { it.name.equals(x.category, true) }?.name
                    ?: if (x.income) "Income" else com.aicanvas.app.data.Expenses.guessCategory(x.category + " " + x.note, expenseSettings.categories)
                saveExpense(x.copy(category = cat))
            }
        }
    }

    /** A few lines about the app's own lists, so the AI can answer "what did I spend?" or "what's due?". */
    private fun moduleSummary(): String {
        val today = LocalDate.now()
        val t = com.aicanvas.app.data.Expenses.totals(expenses.toList(), today)
        val cur = expenseSettings.currency
        val open = tasks.filter { !it.done }
        return buildString {
            if (open.isNotEmpty()) append("tasks open: ").append(open.take(15).joinToString("; ") { it.title + (it.due?.let { d -> " (due $d)" } ?: "") }).append('\n')
            if (habits.isNotEmpty()) append("habits: ").append(habits.joinToString("; ") { "${it.name} streak ${com.aicanvas.app.data.Habits.streak(it, today)}" + if (today.toString() in it.done) " done today" else "" }).append('\n')
            if (countdowns.isNotEmpty()) append("countdowns: ").append(countdowns.joinToString("; ") { "${it.title} ${it.date}" }).append('\n')
            if (expenses.isNotEmpty()) {
                append("expenses ($cur): today ${com.aicanvas.app.data.Expenses.money(t.today)}, week ${com.aicanvas.app.data.Expenses.money(t.week)}, month ${com.aicanvas.app.data.Expenses.money(t.month)}")
                if (expenseSettings.budget > 0) append(", budget ${com.aicanvas.app.data.Expenses.money(expenseSettings.budget)}")
                append("; by category: ").append(com.aicanvas.app.data.Expenses.byCategory(expenses.toList(), today).joinToString(", ") { "${it.first} ${com.aicanvas.app.data.Expenses.money(it.second)}" })
                append("; categories: ").append(expenseSettings.categories.joinToString(",") { it.name })
            }
        }.trim()
    }

    var textSize by mutableStateOf(store.textSize)
        private set
    fun chooseTextSize(v: String) { textSize = v; store.textSize = v }
    fun setAccentColor(name: String) { accent = name; store.accent = name }

    /** When the morning briefing goes off ("HH:mm"), or "" when it is off. */
    var briefingTime by mutableStateOf(store.briefingTime)
        private set
    /** Whether the AI sees the phone calendar (off until the user turns it on and allows it). */
    var useCalendar by mutableStateOf(store.useCalendar && PhoneCalendar.granted(app))
        private set
    /** Set when the user turns the calendar on, so the screen can ask for permission. */
    var wantsCalendarPermission by mutableStateOf(false)
    /** Set by the launcher's "Talk" shortcut: the screen opens voice input. */
    var wantsVoice by mutableStateOf(false)
    /** The element the AI is redesigning after a tap inside it, for a small spinner. */
    var workingOn by mutableStateOf<String?>(null)
        private set
    /** True while the AI tidies the screen (new day, or "Tidy up now"). */
    var tidying by mutableStateOf(false)
        private set
    /** The AI's last short answer, shown above the chat box. */
    var reply by mutableStateOf<String?>(null)
        private set
    var replyIsError by mutableStateOf(false)
        private set
    /** Set when the first reminder is made, so the screen can ask for notification permission. */
    var wantsNotifications by mutableStateOf(false)
    /** The screen as it was before the last delete or AI change, so one tap can put it back. */
    var undo by mutableStateOf<Undo?>(null)
        private set
    /** Set by "Change with AI": the next chat message is about this element. */
    private var focusId by mutableStateOf<String?>(null)

    val chatHint: String
        get() = when {
            focusId != null -> "What should change?"
            photos.isNotEmpty() -> if (photos.size > 1) "Say something about the photos…" else "Say something about the photo…"
            else -> "Tell me anything…"
        }

    init {
        // Startup extras must never stop the app from opening: a bad record is skipped, not fatal.
        runCatching {
            if (!store.expensesMigrated) {
                val found = elements.flatMap { e -> runCatching { com.aicanvas.app.data.Expenses.fromCard(e.ui, e.createdAt, expenseSettings.categories) }.getOrDefault(emptyList()) }
                if (found.isNotEmpty()) { expenses.addAll(found) }
                store.expensesMigrated = true
                store.saveExpenses(expenses.toList())
            }
        }.onFailure { android.util.Log.e("AiCanvas", "expense migration failed", it); store.expensesMigrated = true }
        runCatching { ensureModuleCards() }.onFailure { android.util.Log.e("AiCanvas", "home cards failed", it) }
        if (Store.loadFailures.isNotEmpty()) {
            reply = "Some saved data couldn't be read. A backup copy was kept on the phone."
            replyIsError = true
        }
    }

    /** Recent (role, content) turns so follow-ups like "make it green" work. Not saved. */
    private val history = mutableListOf<Pair<String, String>>()

    // ---------------- panels ----------------
    fun openChat() {
        if (!settings.isConfigured) return openSettings()
        focusId = null
        chatOpen = true
    }

    fun changeWithAi(id: String) {
        if (!settings.isConfigured) return openSettings()
        focusId = id
        reply = null
        chatOpen = true
    }

    fun openSettings() {
        chatOpen = false
        menuOpen = true
        menuPage = "main"
        menuDirect = false
    }

    /** Opened from the quick menu straight onto one page; back then closes instead of showing Settings. */
    var menuDirect by mutableStateOf(false)
    fun openPage(page: String) {
        if (page == "main") return openSettings()
        chatOpen = false
        menuPage = page
        menuDirect = true
        menuOpen = true
    }

    fun closeMenu() {
        menuOpen = false
        menuPage = "main"
    }

    fun saveSettings(s: AppSettings) {
        settings = s
        store.saveSettings(s)
        testResult = null
    }

    // ---------------- canvases ----------------
    private fun saveCanvases() { store.canvasesJson = com.aicanvas.app.data.Canvases.serialize(canvases.toList()) }

    fun switchCanvas(id: String) { if (canvases.any { it.id == id }) { currentCanvas = id; store.currentCanvas = id } }

    fun createCanvas(name: String) {
        val n = name.trim().ifBlank { return }
        val (list, c) = com.aicanvas.app.data.Canvases.ensure(canvases.toList(), n)
        canvases.clear(); canvases.addAll(list); saveCanvases(); switchCanvas(c.id)
    }

    fun renameCanvas(id: String, name: String) {
        val i = canvases.indexOfFirst { it.id == id }
        if (i < 0 || name.isBlank()) return
        canvases[i] = canvases[i].copy(name = name.trim()); saveCanvases()
    }

    /** Deletes a canvas; its cards move to Home (nothing is lost). Home itself can't be deleted. */
    fun deleteCanvas(id: String) {
        if (id == com.aicanvas.app.data.Canvases.HOME_ID) return
        val before = elements.toList()
        val canvasesBefore = canvases.toList()
        elements.indices.forEach { i -> if (elements[i].canvas == id) elements[i] = elements[i].copy(canvas = com.aicanvas.app.data.Canvases.HOME_ID) }
        canvases.removeAll { it.id == id }; saveCanvases()
        if (currentCanvas == id) switchCanvas(com.aicanvas.app.data.Canvases.HOME_ID)
        undo = Undo("Canvas deleted", before, canvases = canvasesBefore)
        saveElements()
    }

    /** Merges the picked canvases into one named [name]; cards move, the other canvases go. Undoable. */
    fun mergeCanvases(ids: List<String>, name: String) {
        val m = com.aicanvas.app.data.Canvases.merge(elements.toList(), canvases.toList(), ids, name) ?: return
        applyMerge(m, "Canvases merged")
    }

    /** Back to one canvas: all cards on Home, the other canvases removed. Undoable. */
    fun mergeAllCanvases() = applyMerge(com.aicanvas.app.data.Canvases.mergeAll(elements.toList(), canvases.toList()), "Merged into one")

    private fun applyMerge(m: com.aicanvas.app.data.Canvases.Merged, label: String) {
        val before = elements.toList()
        val canvasesBefore = canvases.toList()
        elements.clear(); elements.addAll(m.elements)
        canvases.clear(); canvases.addAll(m.canvases); saveCanvases()
        switchCanvas(m.target.id)
        undo = Undo(label, before, canvases = canvasesBefore)
        saveElements()
    }

    // ---------------- reset ----------------
    /** Settings back to defaults: look, briefing, calendar, auto-place. The AI key and model stay unless [aiToo]. */
    fun resetSettings(aiToo: Boolean = false) {
        setTheme("system"); setAccentColor("default"); chooseTextSize("default")
        if (briefingTime.isNotBlank()) { Briefing.set(getApplication(), ""); briefingTime = "" }
        if (useCalendar) { useCalendar = false; store.useCalendar = false }
        if (autoPlace) toggleAutoPlace()
        if (aiToo) saveSettings(AppSettings())
    }

    /** Back to a single empty Home canvas: every card gone too. Undoable until the next change. */
    fun resetCanvases() {
        val canvasesBefore = canvases.toList()
        clearAll()
        canvases.clear(); canvases.add(com.aicanvas.app.data.Canvases.HOME); saveCanvases(); switchCanvas(com.aicanvas.app.data.Canvases.HOME_ID)
        undo = undo?.let { Undo("Canvases reset", it.before, it.reminders, canvases = canvasesBefore) }
    }

    /** Wipes everything (cards, canvases, memories, reminders, tasks, habits, countdowns, settings); not undoable. */
    fun resetAllData(keepKey: Boolean) {
        val app = getApplication<Application>()
        val key = settings
        elements.forEach { Timers.cancelAll(app, it) }
        reminders.forEach { Reminders.cancel(app, it.id) }
        elements.clear(); history.clear(); queue.clear(); queued = 0; reply = null
        reminders.clear(); memories.clear(); tasks.clear(); habits.clear(); countdowns.clear(); expenses.clear()
        store.saveExpenses(emptyList()); saveExpenseSettings(com.aicanvas.app.data.ExpenseSettings())
        hiddenModules = emptySet(); store.hiddenModules = emptySet(); moduleCards = true; store.moduleCards = true
        saveElements(); saveReminders(); saveMemories()
        store.saveTasks(emptyList()); store.saveHabits(emptyList()); store.saveCountdowns(emptyList())
        canvases.clear(); canvases.add(com.aicanvas.app.data.Canvases.HOME); saveCanvases(); switchCanvas(com.aicanvas.app.data.Canvases.HOME_ID)
        resetSettings(aiToo = true)
        if (keepKey) saveSettings(key)
        undo = null
    }

    fun toggleAutoPlace() { autoPlace = !autoPlace; store.autoPlace = autoPlace }

    /** The proposed auto-organize moves, shown for confirmation; null when no preview is open. */
    var organizePlan by mutableStateOf<List<com.aicanvas.app.data.Canvases.Move>?>(null)
        private set
    var organizing by mutableStateOf(false)
        private set

    /** Works out which canvas each card belongs on: the AI when set up, otherwise the keyword classifier. */
    fun planOrganize() {
        if (organizing) return
        organizing = true
        viewModelScope.launch {
            val list = elements.toList()
            val ai: Map<String, String> = if (settings.isConfigured && Network.isOnline(getApplication())) {
                runCatching { Ai.classify(settings, list.associate { it.id to com.aicanvas.app.data.Canvases.textOf(it.ui).take(300) }, com.aicanvas.app.data.Canvases.CATEGORIES) }.getOrDefault(emptyMap())
            } else emptyMap()
            organizePlan = com.aicanvas.app.data.Canvases.plan(list, canvases.toList()) { e ->
                ai[e.id] ?: com.aicanvas.app.data.Canvases.classify(com.aicanvas.app.data.Canvases.textOf(e.ui))
            }
            organizing = false
        }
    }

    fun cancelOrganize() { organizePlan = null }

    /** Applies the previewed moves (creating category canvases as needed). Undo puts every card back. */
    fun applyOrganize() {
        val plan = organizePlan ?: return
        organizePlan = null
        if (plan.isEmpty()) return
        val before = elements.toList()
        val canvasesBefore = canvases.toList()
        var list = canvases.toList()
        plan.forEach { m ->
            val (l, c) = com.aicanvas.app.data.Canvases.ensure(list, m.category); list = l
            val i = elements.indexOfFirst { it.id == m.elementId }
            if (i >= 0) elements[i] = elements[i].copy(canvas = c.id)
        }
        canvases.clear(); canvases.addAll(list); saveCanvases()
        undo = Undo("Organized", before, canvases = canvasesBefore)
        saveElements()
    }

    // ---------------- live cards ----------------
    fun specFor(e: Element) = e.refresh ?: com.aicanvas.app.data.Refresh.guess(e.ui, titleOf(e.ui))

    /** True while live cards are being refreshed. */
    var refreshing by mutableStateOf(false)
        private set

    /**
     * Refreshes live cards in place (same id): fresh tool data first, then the AI rewrites only those cards.
     * With no key or no connection the old content stays and the card shows when it was last updated.
     */
    fun refreshLive(force: Boolean = false) {
        if (busy || refreshing || !settings.isConfigured || !Network.isOnline(getApplication())) return
        val now = System.currentTimeMillis()
        val due = elements.filter { e -> specFor(e) != null && (force || com.aicanvas.app.data.Refresh.isStale(specFor(e), e.refreshedAt, now)) }
        if (due.isEmpty()) return
        refreshing = true
        viewModelScope.launch {
            val lookups = due.mapNotNull { specFor(it) }.filter { it.optString("kind") in setOf("weather", "news", "fx") }.distinctBy { it.toString() }
            val fresh = if (lookups.isEmpty()) "" else runCatching { Lookup.run(lookups) }.getOrNull()
                ?.let { "\n<lookup_results>\n$it\n</lookup_results>" } ?: ""
            val ids = due.map { it.id }
            refreshIds = ids.toSet()
            run(com.aicanvas.app.data.Refresh.prompt(ids) + fresh, silent = true, onSuccess = {
                val t = System.currentTimeMillis()
                elements.indices.forEach { i -> if (elements[i].id in ids) elements[i] = elements[i].copy(refresh = specFor(elements[i]), refreshedAt = t) }
                saveElements()
            })
        }
    }
    /** Cards the running refresh may touch; anything else it returns is ignored. */
    private var refreshIds: Set<String>? = null

    /** Called whenever the app comes to the front. */
    fun onResume() {
        // The daily background refresh may have rewritten the cards while the app was in the background.
        if (store.backgroundRefreshAt > seenBackgroundRefresh && !busy) {
            seenBackgroundRefresh = store.backgroundRefreshAt
            val fresh = store.loadElements().associateBy { it.id }
            elements.indices.forEach { i -> fresh[elements[i].id]?.let { f -> if (f.refreshedAt > elements[i].refreshedAt) elements[i] = elements[i].copy(ui = f.ui, refresh = f.refresh, refreshedAt = f.refreshedAt) } }
        }
        // Fired reminders are removed (or moved on, if repeating) by the receiver while the app is closed.
        reminders.clear()
        reminders.addAll(store.loadReminders())
        // Focus timers that finished while the app was closed are counted now.
        val now = System.currentTimeMillis()
        var counted = false
        elements.indices.forEach { i ->
            val ui = JSONObject(elements[i].ui.toString())
            if (com.aicanvas.app.data.CardLogic.refresh(ui, now)) { elements[i] = elements[i].copy(ui = ui); counted = true }
        }
        if (counted) saveElements()
        // Recurring expenses due since last time are added.
        val made = runCatching { com.aicanvas.app.data.Expenses.materialize(expenses.toList(), LocalDate.now()) }.getOrDefault(expenses.toList())
        if (made.size != expenses.size || made != expenses.toList()) { expenses.clear(); expenses.addAll(made); store.saveExpenses(made) }
        val today = LocalDate.now().toString()
        if (store.lastTidy != today && elements.isNotEmpty() && !busy) {
            val lastDay = runCatching { LocalDate.parse(store.lastTidy) }.getOrNull()
            store.lastTidy = today
            rollOverDailyCounters(lastDay ?: LocalDate.now().minusDays(1))
            if (settings.isConfigured) tidy("[New day] Tidy the screen for today.")
        } else if (store.lastTidy.isEmpty()) {
            store.lastTidy = today
        }
        if (!tidying) refreshLive()
    }

    /**
     * On a new day, daily counters (water, steps…) go back to 0. The old value is kept in the counter's
     * "history" (last 7 days), which the card shows as small bars.
     */
    private fun rollOverDailyCounters(day: LocalDate) {
        val label = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        var changed = false
        elements.indices.forEach { i ->
            val ui = JSONObject(elements[i].ui.toString())
            if (com.aicanvas.app.data.CardLogic.rollOver(ui, label)) {
                elements[i] = elements[i].copy(ui = ui)
                changed = true
            }
        }
        if (changed) saveElements()
    }

    /** Menu → "Tidy up now", or pulling the screen down. */
    fun tidyNow() {
        if (busy || tidying || !settings.isConfigured || elements.isEmpty()) return
        tidy("[Tidy] Tidy up the screen now.")
    }

    /**
     * Fetches fresh data for the live cards first (so the AI updates them from real numbers rather than
     * guessing), then lets the AI tidy.
     */
    private fun tidy(message: String) {
        tidying = true
        viewModelScope.launch {
            val lookups = elements.mapNotNull { liveLookupFor(it) }.distinctBy { it.toString() }
            val fresh = if (lookups.isEmpty()) "" else runCatching { Lookup.run(lookups) }.getOrNull()
                ?.let { "\n<lookup_results>\n$it\n</lookup_results>\nUpdate the live cards from these fresh results." } ?: ""
            run(message + fresh, silent = true)
        }
    }

    /** A weather card is looked up by its title (the place); a news card gets the top headlines. */
    private fun liveLookupFor(e: Element): JSONObject? {
        val text = e.ui.toString().lowercase()
        return when {
            "humidity" in text || "feels like" in text -> titleOf(e.ui)?.let { JSONObject().put("kind", "weather").put("query", it) }
            "headline" in text || "news" in text -> JSONObject().put("kind", "news").put("query", "")
            else -> null
        }
    }

    private fun titleOf(node: JSONObject): String? {
        val texts = mutableListOf<JSONObject>()
        fun walk(n: JSONObject) {
            if (n.optString("type") == "text") texts += n
            val c = n.optJSONArray("children") ?: return
            for (i in 0 until c.length()) c.optJSONObject(i)?.let(::walk)
        }
        walk(node)
        return (texts.firstOrNull { it.optString("style") == "title" } ?: texts.firstOrNull())?.optString("text")?.ifBlank { null }
    }

    /**
     * Tidy-ups may only throw away cards with nothing left to do. Timers, counters, cards you type into and
     * checklists with open items stay unless you delete them yourself.
     */
    private fun safeToTidyAway(e: Element): Boolean {
        if (com.aicanvas.app.data.Modules.kindOf(e.ui) != null) return false
        var keep = false
        fun walk(n: JSONObject) {
            when (n.optString("type")) {
                "timer", "counter", "input" -> keep = true
                "check" -> if (!n.optBoolean("checked")) keep = true
            }
            val c = n.optJSONArray("children") ?: return
            for (i in 0 until c.length()) c.optJSONObject(i)?.let(::walk)
        }
        walk(e.ui)
        return !keep
    }

    // ---------------- photos and sharing ----------------
    private fun squeeze(bitmap: Bitmap, budget: Int): ByteArray {
        var jpeg = ByteArray(0)
        for (q in com.aicanvas.app.data.Limits.PHOTO_QUALITIES) {
            jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
            if (jpeg.size <= budget) break
        }
        return jpeg
    }

    /** Adds picked, pasted or shared photos (as many as still fit), each shrunk to a JPEG small enough to send. */
    fun attachPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val room = com.aicanvas.app.data.Limits.roomFor(photos.size, uris.size)
        if (room < uris.size) {
            reply = "You can send up to ${com.aicanvas.app.data.Limits.MAX_PHOTOS} photos at once" + if (room > 0) ", so I added the first $room." else "."
            replyIsError = room == 0
        }
        uris.take(room).forEach { attachPhoto(it) }
    }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                    val sample = com.aicanvas.app.data.Limits.sampleSize(bounds.outWidth, bounds.outHeight)
                    val decoded = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching null
                    val scale = 1280f / maxOf(decoded.width, decoded.height)
                    val bitmap = if (scale < 1f) {
                        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                    } else decoded
                    Attached(bitmap, squeeze(bitmap, com.aicanvas.app.data.Limits.MAX_PHOTO_BYTES))
                }.getOrElse { null }
            }
            when {
                result == null -> {
                    reply = "Couldn't open that photo. It may be too large or not an image; try a screenshot or a smaller one."
                    replyIsError = true
                }
                photos.size < com.aicanvas.app.data.Limits.MAX_PHOTOS -> photos += result
            }
        }
    }

    fun tooManyPhotos() {
        reply = "You can send up to ${com.aicanvas.app.data.Limits.MAX_PHOTOS} photos at once. Remove one to add another."
        replyIsError = true
    }

    fun removePhoto(index: Int) { if (index in photos.indices) photos.removeAt(index) }

    /** The JPEGs to send; if together they are too heavy, each is squeezed to its share of the total. */
    private suspend fun photosToSend(list: List<Attached>): List<ByteArray> {
        if (list.sumOf { it.jpeg.size } <= com.aicanvas.app.data.Limits.MAX_TOTAL_PHOTO_BYTES) return list.map { it.jpeg }
        val budget = com.aicanvas.app.data.Limits.photoBudget(list.size)
        return withContext(Dispatchers.Default) { list.map { if (it.jpeg.size <= budget) it.jpeg else squeeze(it.preview, budget) } }
    }

    /** Menu → "Morning briefing": Off → 7:00 → 8:00 → 9:00 → Off. Turning it on shows a preview right away. */
    fun cycleBriefing() {
        val times = listOf("", "07:00", "08:00", "09:00")
        val next = times[(times.indexOf(briefingTime) + 1) % times.size]
        val app = getApplication<Application>()
        Briefing.set(app, next)
        if (next.isNotBlank()) {
            wantsNotifications = true
            if (briefingTime.isBlank()) Briefing.show(app, "Morning briefing at ${Briefing.label(next)}")
        }
        briefingTime = next
    }

    /** Menu → "Use my calendar". Turning it on asks Android for permission first. */
    fun toggleCalendar() {
        if (useCalendar) {
            useCalendar = false
            store.useCalendar = false
        } else if (PhoneCalendar.granted(getApplication())) {
            calendarAllowed(true)
        } else {
            wantsCalendarPermission = true
        }
    }

    /** The chat should open the photo picker (a "Photo → cards" starter was tapped). */
    var wantsPhoto by mutableStateOf(false)
    private var pendingStarter: com.aicanvas.app.data.Starter? = null

    /** A starter chip was tapped: get what it needs (a photo, calendar access), then send its prompt. */
    fun useStarter(s: com.aicanvas.app.data.Starter) {
        if (!settings.isConfigured) { draft = s.prompt; return send() }
        when {
            s.needs == "photo" -> { draft = s.prompt; focusId = null; chatOpen = true; wantsPhoto = true }
            s.needs == "calendar" && !useCalendar -> { pendingStarter = s; toggleCalendar() }
            else -> { draft = s.prompt; send() }
        }
    }

    /** The user said no to notifications: reminders and timers still run, but can't show anything. */
    fun notificationsDenied() {
        reply = "Notifications are off, so reminders and timers can't alert you. Turn them on in Settings → Calendar & permissions."
        replyIsError = true
    }

    fun calendarAllowed(granted: Boolean) {
        wantsCalendarPermission = false
        if (!granted) {
            reply = "Calendar access was not allowed, so I can't read your events. Allow it in Settings → Calendar & permissions."
            replyIsError = true
        }
        useCalendar = granted
        store.useCalendar = granted
        // A calendar starter was waiting for this answer: run it either way (without events if denied).
        pendingStarter?.let { pendingStarter = null; draft = it.prompt; if (granted) send() }
    }

    /** The launcher shortcuts: "Talk" opens voice input, "Type" opens the chat. */
    fun shortcut(talk: Boolean) {
        if (!settings.isConfigured) return openSettings()
        if (talk) wantsVoice = true else openChat()
    }

    /** Something shared from another app: put it in the chat, ready to send. */
    fun receiveShare(text: String?, images: List<Uri>) {
        if (!text.isNullOrBlank()) draft = text.trim()
        attachPhotos(images)
        if (settings.isConfigured) {
            focusId = null
            reply = null
            chatOpen = true
        } else {
            openSettings()
        }
    }

    // ---------------- talking to the AI ----------------
    private companion object {
        const val MAX_QUEUED = 5
    }
    /** The last message was cut to [com.aicanvas.app.data.Limits.MAX_MESSAGE] characters; the reply mentions it. */
    private var longInputNote = false

    fun send() {
        val text = draft.trim()
        val attached = photos.toList()
        if ((text.isEmpty() && attached.isEmpty()) || busy) return
        if (!settings.isConfigured) {
            reply = com.aicanvas.app.data.Readiness.problem(hasKey = false, online = true)
            replyIsError = true
            return openSettings()
        }
        val about = focusId?.let { "[About element $it] " } ?: ""
        val (clipped, cut) = com.aicanvas.app.data.Limits.clip(text)
        draft = ""
        focusId = null
        photos.clear()
        viewModelScope.launch {
            val images = photosToSend(attached)
            run(about + clipped + if (cut) "\n[The user's message was longer and was cut here.]" else "", images = images, onError = { draft = text })
        }
        if (cut) longInputNote = true
    }

    /** A button or input inside an element was used: let the AI react by updating that element. */
    fun press(id: String, label: String, prompt: String) {
        if (!settings.isConfigured) return openSettings()
        if (busy) {
            // Don't drop what the user typed or tapped: do it as soon as the AI is free.
            if (queue.size < MAX_QUEUED) {
                queue.addLast(Pressed(id, label, prompt))
                queued = queue.size
            } else {
                reply = "Still working on the last few things. Try again in a moment."
                replyIsError = true
            }
            return
        }
        workingOn = id
        run("[In element $id the user used \"$label\"] $prompt", onError = { chatOpen = true })
    }

    private data class Pressed(val id: String, val label: String, val prompt: String)
    private val queue = ArrayDeque<Pressed>()
    /** How many card taps/inputs are waiting for the AI to be free. */
    var queued by mutableStateOf(0)
        private set

    private fun runNextQueued() {
        val next = queue.removeFirstOrNull() ?: return
        queued = queue.size
        if (elements.none { it.id == next.id }) return runNextQueued()
        press(next.id, next.label, next.prompt)
    }

    private var seenBackgroundRefresh = store.backgroundRefreshAt

    private fun run(message: String, silent: Boolean = false, images: List<ByteArray> = emptyList(), onError: () -> Unit = {}, onSuccess: () -> Unit = {}) {
        if (!Network.isOnline(getApplication())) {
            if (!silent) {
                reply = Network.OFFLINE_MESSAGE
                replyIsError = true
                onError()
            }
            queue.clear()
            queued = 0
            return
        }
        busy = true
        status = when { images.size > 1 -> "Reading the photos…"; images.isNotEmpty() -> "Reading the photo…"; else -> "Thinking…" }
        if (!silent) {
            reply = null
            replyIsError = false
        }
        val runId = System.nanoTime()
        val started = System.currentTimeMillis()
        runs.add(0, com.aicanvas.app.ai.AiRun(runId, message.take(300), settings.model.trim()))
        while (runs.size > 12) runs.removeAt(runs.lastIndex)
        viewModelScope.launch {
            try {
                val calendar = if (useCalendar) withContext(Dispatchers.IO) { PhoneCalendar.upcoming(getApplication()) } else emptyList()
                val app = getApplication<Application>()
                val calendarLookup: ((org.json.JSONObject) -> String)? = if (useCalendar) { req -> PhoneCalendar.lookup(app, req) } else null
                val snap = Snapshot(elements.toList(), memories.toList(), reminders.toList(), calendar, calendarLookup, canvases.toList(), currentCanvas, moduleSummary())
                val r = Ai.ask(settings, snap, history.toList(), message, images, onStatus = { status = it }, trace = { label, detail -> logEvent(runId, label, detail) })
                val only = refreshIds
                if (only != null) {
                    // A refresh only rewrites the live cards it was asked about: no new cards, nothing removed.
                    apply(Reply(r.text, emptyList(), r.update.filter { it.optString("id") in only }, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), r.raw), "Refreshed")
                } else {
                    // Deletes the user asked for are confirmed first (the rest of the reply applies straight away).
                    val doomed = com.aicanvas.app.data.Canvases.removalIds(elements.toList(), canvases.toList(), r.remove, r.removeAll, currentCanvas)
                    if (!silent && doomed.isNotEmpty()) {
                        apply(r.withRemove(emptyList()), null)
                        pendingDelete = PendingDelete(doomed, doomed.map { id -> elements.find { it.id == id }?.let { titleOf(it.ui) } ?: "Card" })
                    } else apply(r.withRemove(doomed), if (silent) "Tidied" else null)
                }
                onSuccess()
                finishRun(runId, started, "Done", r.raw, "Applied", "${r.create.size} created, ${r.update.size} updated, ${r.remove.size} removed")
                if (!silent) {
                    history += "user" to (if (images.isNotEmpty()) "[photo] " else "") + message
                    history += "assistant" to r.raw
                    while (history.size > 8) history.removeAt(0)
                    reply = listOfNotNull(
                        r.text.ifBlank { "Done." },
                        com.aicanvas.app.data.ReminderPlan.pastNote(r.pastReminders),
                        if (longInputNote) "(Your message was very long, so I read the first ${com.aicanvas.app.data.Limits.MAX_MESSAGE} characters.)" else null,
                    ).joinToString(" ")
                    longInputNote = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finishRun(runId, started, "Failed", "", "Error", Ai.friendlyError(e))
                if (!silent) {
                    reply = Ai.friendlyError(e)
                    replyIsError = true
                    onError()
                }
            } finally {
                busy = false
                workingOn = null
                tidying = false
                refreshing = false
                refreshIds = null
                runNextQueued()
            }
        }
    }

    private fun logEvent(id: Long, label: String, detail: String) {
        val i = runs.indexOfFirst { it.id == id }
        if (i >= 0) runs[i] = runs[i].copy(events = runs[i].events + com.aicanvas.app.ai.AiEvent(label, detail))
    }

    private fun finishRun(id: Long, started: Long, outcome: String, raw: String, label: String, detail: String) {
        logEvent(id, label, detail)
        val i = runs.indexOfFirst { it.id == id }
        if (i >= 0) runs[i] = runs[i].copy(outcome = outcome, raw = raw, elapsedMs = System.currentTimeMillis() - started)
    }

    /** Cards the AI wants to delete, waiting for the user to confirm. */
    var pendingDelete by mutableStateOf<PendingDelete?>(null)
        private set

    fun confirmDelete() {
        val p = pendingDelete ?: return
        pendingDelete = null
        apply(Reply("", emptyList(), emptyList(), p.ids, emptyList(), emptyList(), emptyList(), emptyList(), ""), if (p.ids.size == 1) "Deleted" else "Deleted ${p.ids.size}")
    }

    fun cancelDelete() { pendingDelete = null }

    private fun Reply.withRemove(ids: List<String>) =
        Reply(text, create, update, ids, remember, forget, remind, cancelReminders, raw, pastReminders, add = add)

    private fun apply(r: Reply, undoLabel: String?) {
        if (r.add.isNotEmpty()) applyAdds(r.add)
        val before = elements.toList()
        val canvasesBefore = canvases.toList()
        val remove = if (undoLabel == "Tidied") r.remove.filter { id -> elements.find { it.id == id }?.let(::safeToTidyAway) ?: true } else r.remove
        val app = getApplication<Application>()
        // Removed or redesigned cards lose their timer alarms; running ones are booked again below.
        elements.filter { it.id in remove || r.update.any { u -> u.optString("id") == it.id } }.forEach { Timers.cancelAll(app, it) }
        elements.filter { it.id in remove }.forEach(::hideModuleOf)
        elements.removeAll { it.id in remove }
        val dropped = dropRemindersFor(remove)
        r.update.forEach { u ->
            val i = elements.indexOfFirst { it.id == u.optString("id") }
            if (i < 0) return@forEach
            val e = elements[i]
            elements[i] = e.copy(
                size = sizeOf(u.optString("size")) ?: e.size,
                tone = u.optString("tone").ifBlank { e.tone },
                // Ai.parse already checked the design; keep the user's ticks, counts and timers.
                // Live module cards draw from the app's data; the AI can't redesign them.
                ui = if (com.aicanvas.app.data.Modules.kindOf(e.ui) != null) e.ui else u.optJSONObject("ui")?.let { UiSchema.keepUserState(e.ui, it) } ?: e.ui,
                // Cards the user placed by hand keep their spot.
                importance = if (!e.placed && u.has("importance")) u.optDouble("importance", e.importance).coerceIn(0.0, 100.0) else e.importance,
                // The AI can move a card to another canvas by id or name; a new name makes that canvas.
                canvas = u.optString("canvas").takeIf { it.isNotBlank() }?.let { ref ->
                    com.aicanvas.app.data.Canvases.resolve(canvases.toList(), ref)?.id ?: com.aicanvas.app.data.Canvases.ensure(canvases.toList(), ref).let { (list, c) ->
                        canvases.clear(); canvases.addAll(list); saveCanvases(); c.id
                    }
                } ?: e.canvas,
            )
        }
        // New cards go on the current canvas, or the one matching their category when auto-place is on.
        val now = System.currentTimeMillis()
        val created = r.create.map { e ->
            val spec = e.refresh ?: com.aicanvas.app.data.Refresh.guess(e.ui, titleOf(e.ui))
            val target = if (autoPlace) {
                val (list, c) = com.aicanvas.app.data.Canvases.ensure(canvases.toList(), com.aicanvas.app.data.Canvases.classify(com.aicanvas.app.data.Canvases.textOf(e.ui)))
                if (list.size != canvases.size) { canvases.clear(); canvases.addAll(list); saveCanvases() }
                c.id
            } else currentCanvas
            e.copy(canvas = target, refresh = spec, refreshedAt = if (spec != null) now else 0L)
        }
        elements.addAll(created)
        elements.sortWith(byImportance)
        saveElements()
        elements.filter { e -> r.update.any { it.optString("id") == e.id } }.forEach { Timers.scheduleRunning(app, it) }
        r.create.forEach { startTimers(it.id, it.ui, emptyList()) }
        if (r.create.isNotEmpty() || r.update.isNotEmpty() || remove.isNotEmpty()) {
            val label = undoLabel ?: if (r.update.isEmpty() && remove.isEmpty()) "Added" else "Updated"
            undo = Undo(label, before, dropped, canvases = canvasesBefore.takeIf { it != canvases.toList() })
        }

        if (r.remember.isNotEmpty() || r.forget.isNotEmpty()) {
            memories.removeAll { it.id in r.forget }
            r.remember.forEach { fact -> if (!com.aicanvas.app.data.MemoryRank.isDuplicate(fact, memories)) memories += Memory(text = fact) }
            saveMemories()
        }

        if (r.remind.isNotEmpty() || r.cancelReminders.isNotEmpty()) {
            reminders.filter { it.id in r.cancelReminders }.forEach { Reminders.cancel(app, it.id) }
            reminders.removeAll { it.id in r.cancelReminders }
            // A reminder made alongside a single card belongs to that card.
            val owner = r.create.singleOrNull()?.id ?: r.update.singleOrNull()?.optString("id").orEmpty()
            val remind = r.remind.map { it.copy(elementId = owner) }
            remind.forEach { Reminders.schedule(app, it.id, it.at, it.text) }
            reminders.addAll(remind)
            saveReminders()
            if (r.remind.isNotEmpty()) wantsNotifications = true
        }
    }

    // ---------------- things the user does on an element directly ----------------
    /** Changes one node inside an element (a tick, a counter). [path] is the child indexes down to it. */
    fun updateNode(id: String, path: List<Int>, change: (JSONObject) -> Unit) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        val ui = JSONObject(elements[i].ui.toString())
        var node: JSONObject = ui
        for (index in path) node = node.optJSONArray("children")?.optJSONObject(index) ?: return
        change(node)
        com.aicanvas.app.data.CardLogic.refresh(ui, System.currentTimeMillis())
        elements[i] = elements[i].copy(ui = ui)
        saveElements()
    }

    /** Starts ([endsAt] > 0), pauses ([left] > 0) or resets a timer, with a notification when it ends. */
    fun setTimer(id: String, path: List<Int>, endsAt: Long, left: Long, label: String) {
        updateNode(id, path) { it.put("endsAt", endsAt).put("left", left) }
        val app = getApplication<Application>()
        val key = Timers.key(id, path)
        if (endsAt > 0) {
            Reminders.schedule(app, key, endsAt, "⏱ $label is done")
            wantsNotifications = true
        } else {
            Reminders.cancel(app, key)
        }
    }

    /** Timers the AI marked "start": true begin running as soon as their element appears. */
    private fun startTimers(id: String, node: JSONObject, path: List<Int>) {
        if (node.optString("type") == "timer" && node.optBoolean("start") && node.optLong("endsAt") == 0L) {
            val minutes = node.optDouble("minutes", 25.0)
            setTimer(id, path, System.currentTimeMillis() + (minutes * 60_000).toLong(), 0, node.optString("label", "Timer"))
        }
        val children = node.optJSONArray("children") ?: return
        for (i in 0 until children.length()) children.optJSONObject(i)?.let { startTimers(id, it, path + i) }
    }

    // ---------------- order ----------------
    /** While dragging: put [id] where [targetId] is. Saved when the drag ends. */
    fun moveOnto(id: String, targetId: String) {
        val from = elements.indexOfFirst { it.id == id }
        val to = elements.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return
        elements.add(to, elements.removeAt(from))
    }

    /** The drag ended: remember the new spot by giving the card an importance between its neighbours. */
    fun finishMove(id: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        val above = elements.getOrNull(i - 1)?.importance
        val below = elements.getOrNull(i + 1)?.importance
        val importance = when {
            above == null && below == null -> elements[i].importance
            above == null -> below!! + 5
            below == null -> above - 5
            else -> (above + below) / 2
        }
        elements[i] = elements[i].copy(importance = importance, placed = true)
        keepOrder()
        saveElements()
    }

    /** Tap on a card: fold it to its summary or open it fully. */
    fun toggleExpanded(id: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        elements[i] = elements[i].copy(expanded = !elements[i].expanded)
        saveElements()
    }

    fun moveToTop(id: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i <= 0) return
        elements.add(0, elements.removeAt(i))
        finishMove(id)
    }

    /** Moves a card one place up or down: the non-gesture way to reorder (menu and TalkBack). */
    fun moveBy(id: String, delta: Int) {
        val i = elements.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= elements.size) return
        elements.add(j, elements.removeAt(i))
        finishMove(id)
    }

    /** Hands a card the user placed back to the AI's automatic ordering. */
    fun letAiPlace(id: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        elements[i] = elements[i].copy(placed = false)
        saveElements()
    }

    /** Nudges importances so that sorting gives exactly the current order (needed when neighbours are equal). */
    private fun keepOrder() {
        for (i in 1 until elements.size) {
            if (elements[i].importance >= elements[i - 1].importance) {
                elements[i] = elements[i].copy(importance = elements[i - 1].importance - 0.01)
            }
        }
    }

    fun remove(id: String) {
        val i = elements.indexOfFirst { it.id == id }
        if (i < 0) return
        val before = elements.toList()
        Timers.cancelAll(getApplication(), elements[i])
        hideModuleOf(elements[i])
        elements.removeAt(i)
        undo = Undo("Deleted", before, dropRemindersFor(listOf(id)))
        saveElements()
    }

    fun undoLast() {
        val u = undo ?: return
        val app = getApplication<Application>()
        elements.forEach { Timers.cancelAll(app, it) }
        elements.clear()
        elements.addAll(u.before)
        elements.forEach { Timers.scheduleRunning(app, it) }
        // Bring back the reminders that went with deleted cards, if they haven't passed.
        val now = System.currentTimeMillis()
        u.reminders.filter { it.at > now || it.repeat.isNotEmpty() }.forEach { r ->
            val at = if (r.repeat.isNotEmpty()) Reminders.nextAfter(r.at, r.repeat, now) else r.at
            Reminders.schedule(app, r.id, at, r.text)
            reminders += r.copy(at = at)
        }
        if (u.reminders.isNotEmpty()) saveReminders()
        if (u.memories.isNotEmpty()) {
            u.memories.filter { m -> memories.none { it.id == m.id } }.forEach { memories += it }
            saveMemories()
        }
        u.canvases?.let { list ->
            canvases.clear(); canvases.addAll(list); saveCanvases()
            if (canvases.none { it.id == currentCanvas }) switchCanvas(com.aicanvas.app.data.Canvases.HOME_ID)
        }
        // A module card brought back by Undo is shown again.
        val back = elements.mapNotNull { com.aicanvas.app.data.Modules.kindOf(it.ui) }.toSet()
        if (hiddenModules.any { it in back }) { hiddenModules = hiddenModules - back; store.hiddenModules = hiddenModules }
        undo = null
        saveElements()
    }

    /** Cancels the reminders that belong to these cards, and returns them (for Undo). */
    private fun dropRemindersFor(ids: Collection<String>): List<Reminder> {
        val gone = reminders.filter { it.elementId.isNotEmpty() && it.elementId in ids }
        if (gone.isEmpty()) return gone
        val app = getApplication<Application>()
        gone.forEach { Reminders.cancel(app, it.id) }
        reminders.removeAll(gone)
        saveReminders()
        return gone
    }

    fun dismissUndo() {
        undo = null
    }

    /** Settings → Test connection. null = not run yet; [testing] while it runs. */
    var testResult by mutableStateOf<Pair<Boolean, String>?>(null)
        private set
    var testing by mutableStateOf(false)
        private set

    fun testConnection(s: AppSettings) {
        if (testing) return
        testResult = null
        if (!Network.isOnline(getApplication())) { testResult = false to Network.OFFLINE_MESSAGE; return }
        testing = true
        val job = viewModelScope.launch {
            testResult = try {
                Ai.testConnection(s)
                true to "Connected. ${s.model} is working."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false to Ai.friendlyError(e)
            } finally {
                testing = false
            }
        }
        // Belt and braces: the spinner always stops, even if a network call ignores its timeouts.
        viewModelScope.launch {
            delay((Ai.TEST_TIMEOUT_S + 8) * 1000)
            if (job.isActive) { job.cancel(); testing = false; testResult = false to "Timed out after ${Ai.TEST_TIMEOUT_S} s with no answer. Check the Base URL, model and connection." }
        }
    }

    fun clearTestResult() { testResult = null }

    /** Free OpenRouter models for the picker in Settings; null until loaded. */
    var freeModels by mutableStateOf<List<com.aicanvas.app.ai.FreeModel>?>(null)
        private set
    var loadingModels by mutableStateOf(false)
        private set

    fun loadFreeModels() {
        if (loadingModels) return
        if (!Network.isOnline(getApplication())) { testResult = false to Network.OFFLINE_MESSAGE; return }
        loadingModels = true
        viewModelScope.launch {
            try { freeModels = com.aicanvas.app.ai.OpenRouter.discover() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { testResult = false to Ai.friendlyError(e) }
            finally { loadingModels = false }
        }
    }

    /** Settings → Export: everything on screen and in memory as one JSON text, to share or keep. */
    fun exportJson(): String = org.json.JSONObject()
        .put("app", "AI Canvas")
        .put("exportedAt", java.time.LocalDateTime.now().toString())
        .put("cards", org.json.JSONArray().apply { elements.forEach { put(it.toJson()) } })
        .put("memories", org.json.JSONArray().apply { memories.forEach { put(it.text) } })
        .put("reminders", org.json.JSONArray().apply {
            reminders.forEach { put(org.json.JSONObject().put("at", it.at).put("text", it.text).put("repeat", it.repeat)) }
        })
        .toString(2)

    fun clearAll() {
        val before = elements.toList()
        undo = Undo("Cleared", before, dropRemindersFor(elements.map { it.id }))
        elements.forEach { Timers.cancelAll(getApplication(), it) }
        elements.clear()
        history.clear()
        queue.clear()
        queued = 0
        reply = null
        saveElements()
    }

    fun forget(id: String) {
        val gone = memories.filter { it.id == id }
        memories.removeAll { it.id == id }
        saveMemories()
        if (gone.isNotEmpty()) undo = Undo("Forgot a memory", elements.toList(), memories = gone)
    }

    private fun saveElements() {
        val list = elements.toList()
        writeInBackground { store.saveElements(list).also { HomeWidget.update(getApplication()) } }
    }

    private fun saveMemories() {
        val list = memories.toList()
        writeInBackground { store.saveMemories(list) }
    }

    private fun saveReminders() {
        val list = reminders.toList()
        writeInBackground { store.saveReminders(list) }
    }


    private fun writeInBackground(write: () -> Boolean) {
        viewModelScope.launch(writer) {
            if (!write()) withContext(Dispatchers.Main) { saveFailed() }
        }
    }

    /** Tells the user that a change could not be stored on the phone. */
    private fun saveFailed() {
        reply = "Couldn't save your changes on this phone. Check free storage and try again."
        replyIsError = true
    }
}

class Undo(val label: String, val before: List<Element>, val reminders: List<Reminder> = emptyList(), val memories: List<Memory> = emptyList(),
           /** The canvases to put back, when the change added, renamed or removed some. */
           val canvases: List<com.aicanvas.app.data.CanvasInfo>? = null)

/** AI-requested deletes awaiting confirmation: ids plus card titles for the dialog. */
class PendingDelete(val ids: List<String>, val titles: List<String>)
