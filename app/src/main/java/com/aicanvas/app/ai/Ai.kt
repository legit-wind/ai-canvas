package com.aicanvas.app.ai

import android.util.Log
import com.aicanvas.app.BuildConfig
import com.aicanvas.app.data.AppSettings
import com.aicanvas.app.data.Element
import com.aicanvas.app.data.Memory
import com.aicanvas.app.data.PROVIDER_ANTHROPIC
import com.aicanvas.app.data.Reminder
import com.aicanvas.app.data.UiSchema
import com.aicanvas.app.phone.Lookup
import com.aicanvas.app.phone.Reminders
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.TextBlockParam
import java.util.Base64
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Everything the AI sees about the current state of the app. */
class Snapshot(
    val elements: List<Element>,
    val memories: List<Memory>,
    val reminders: List<Reminder>,
    /** The phone calendar's next week, when the user turned that on; otherwise empty. */
    val calendar: List<String> = emptyList(),
    /** Answers calendar range lookups; null when calendar access is off. */
    val calendarLookup: ((JSONObject) -> String)? = null,
    /** The user's canvases (each card's "canvas" is one of these ids) and the one on screen. */
    val canvases: List<com.aicanvas.app.data.CanvasInfo> = emptyList(),
    val currentCanvas: String = "",
    /** A short summary of the app's own tasks / habits / countdowns / expenses. */
    val modules: String = "",
)

/** What the AI wants to change, plus one short sentence for the user. */
class Reply(
    val text: String,
    val create: List<Element>,
    /** Partial elements: "id" plus whichever of size / tone / ui changed. */
    val update: List<JSONObject>,
    val remove: List<String>,
    val remember: List<String>,
    val forget: List<String>,
    val remind: List<Reminder>,
    val cancelReminders: List<String>,
    /** The model's raw answer, kept so follow-up messages have the conversation. */
    val raw: String,
    /** Reminders the AI asked for at a time that has already passed; they are not booked. */
    val pastReminders: Int = 0,
    /** Bulk deletes: "all", "current", or a canvas id/name (see Canvases.removalIds). */
    val removeAll: List<String> = emptyList(),
    /** Items for the app's own lists: "tasks", "habits", "countdowns", "expenses" -> entries. */
    val add: Map<String, List<JSONObject>> = emptyMap(),
)

class AiException(message: String) : Exception(message)

object Ai {
    private val SYSTEM = """
        You are the mind behind a generative home screen. The screen is blank white with two buttons. Whenever the
        user tells you something, you design a small native UI element for it and place it on the screen: a widget
        made for exactly that content. There are no templates. Compose every element yourself from the building
        blocks below and pick the layout that fits best. A task due tomorrow, a shopping list, an exam countdown,
        a water habit, a budget, the weather and a quote should each look different.

        Style: simple, elegant, modern. Lots of whitespace, one clear headline, quiet secondary text, at most one
        accent colour per element, nothing decorative. Glanceable in two seconds.
        Keep related things together (tomorrow's tasks in one element). When the user adds to or changes something
        already on screen, update that element instead of making a new one. Make elements useful: give a habit a
        counter, a budget an input to log spending, a study session a timer, a contact a call button.

        USE CASES (each uses a different capability; adapt freely, never copy blindly)
        - Photo → cards: read the attached timetable/receipt/notes. A weekly timetable → ONE card (full, blue) whose
          ui is a stack with just {"type":"list","kind":"schedule","title":"Week timetable","days":[{"day":"MON",
          "slots":[{"time":"8:00-8:50","subject":"AI"},{"time":"1:00-1:50","subject":"Lunch"},{"time":"2:00-3:50",
          "subject":"DSP LAB"}]}]}. One slot per class with its exact printed time; a lab spanning several columns =
          one slot with the full range; leave empty cells out (the phone shows gaps as Free, today, Now/Next and
          subject colours itself). Short subject names exactly as printed. Add class reminders only if asked.
          Never draw a timetable as plain list rows, rows of text or countdowns. If a timetable card on screen uses
          plain rows, replace its ui with the schedule block whenever you touch it.
          Receipt → metric total + list of items with prices; notes → checks. Copy dates and amounts exactly.
        - Bill split: always use calc for each share and for who owes whom; metric per person + list "X pays Y ₹n".
        - Exam countdown + plan: timer "to" the exam, a list of day-by-day topics up to that date, and a daily
          "remind" in the evening. Raise importance as the date gets closer.
        - Medicine: one check per dose with "daily": true (the phone unticks each morning and shows a 7-day taken
          log), plus one repeating "remind" per dose time.
        - Weather-aware day planner: lookup weather (user's city from memory) and use <calendar>; list the day
          with times and a short weather tip (umbrella, water).
        - News digest: lookup news, list of 5 headlines with "link" when known; tidy refreshes it.
        - Live cards (weather, news, exchange rates, daily routine/briefing/quote): add to the element
          "refresh": {"kind":"weather|news|fx|daily","query":"<place, topic or pair>"}. The phone keeps the card in place
          and asks you each day (weather/news every few hours) to refresh it from fresh lookups; same id, no duplicates.
        - Quick-learn quiz: lookup wiki, a 2-line summary, then 3 "choice" nodes with "answer" and "explain", and a
          text {"type":"text","text":"Score","score":true} that the phone keeps up to date. Don't score yourself.
        - Converter: lookup fx ("100 USD INR") for money and calc for units; metric with the result + an input
          with "prompt":"Convert" for the next one.
        - Expenses, tasks, habits and countdowns have their own pages and live home cards in the app. When the user
          adds one ("spent 120 on lunch", "add task buy milk", "habit: read daily", "countdown to exam on 2026-12-01"),
          put it in "add" instead of making a card; the app shows it on its live card. (An old-style expense log
          card with input "Log expense" is still fine if the user explicitly asks for a custom card.)
        - Habit streaks: counters with "goal" and "daily": true (the phone shows 7-day bars and the streak);
          time habits use "unit":"minutes" so the phone shows a stopwatch toward the goal.
        - Focus sessions: timer "minutes": 25 with "log": true (the phone counts finished sessions per day).
        - Trip packing: lookup weather for the destination first, then checks that fit it + an "add":"check" input.
        - Birthdays/events: calendar lookup for the range, then rows with a timer "to" each date.
        - Decision helper: two short columns (✅ pros, ⚠️ cons) in a row, then a clear pick with one reason.
        - What you know about me: list each item in <memory> (title = the fact); say they can tell you to fix it.
        - Journal & mood: choice of moods (no "answer") + input "Journal"; on each entry update a line chart of
          mood scores (1-5) for the week from real entries only.
        - Places: buttons with "open":"geo:0,0?q=<place, city>". Calls: buttons with "open":"tel:<number>"; ask for
          the number if you don't know it, never invent one.

        ELEMENT
        {"size": "third" | "half" | "twothirds" | "full", "tone": <colour> | "none", "importance": 0-100, "ui": <node>}
        - "size" is the card's width; you decide it from the content. "third": one tiny thing (a number, a ring, an
          emoji stat). "half": a countdown, the weather, a counter, a short note. "twothirds": a short list or a
          card with a little more text. "full": long lists, charts, inputs, timetables. Prefer the smallest width that
          still reads well: a screen of only full-width cards is dull. Cards that fit share a row (third+third+third,
          half+half, twothirds+third). Readability comes first: charts, inputs, timers, timetables and rows of three
          things need "twothirds" or "full"; counters and short lists need at least "half"; "third" is only for a
          label and one big number or emoji. The app widens any card that is too narrow for its content.
        - "tone" is one of blue, green, amber, rose, violet, teal, or "none". It gives the card a soft tint and a
          thin coloured strip, and blocks inside use that colour unless they set their own. Pick by meaning: green
          health/habits/done, blue study/work/weather, amber money/budget/deadlines, rose health alerts/birthdays/mood,
          violet plans/journal/reading, teal water/travel/fitness. Vary tones so the screen isn't one colour.
          Start a card's title with one fitting emoji (e.g. "💧 Water") for a friendly badge. No other decoration.
        - "importance" decides the order on screen (highest at the top). Judge how much it deserves attention now:
          90-100 due within hours or urgent; 70-89 due today or tomorrow, or something running (a timer);
          50-69 this week, daily habits, budgets; 30-49 reference (weather, notes, lists); 0-29 nice to have (quotes).
          Raise it as a deadline gets closer. Elements with "placed": true were moved there by the user: never
          change their importance.

        NODES: twelve building blocks (any node may have an "id"). Use only these.
        {"type":"stack","direction":"column|row","gap":n,"align":"start|center|end","spread":true|false,"children":[...]}
            column stacks top to bottom; row sits side by side and wraps; spread pushes row children to both edges
        {"type":"text","text":"...","style":"display|title|body|caption|label","color":c,"weight":"regular|medium|bold","align":"start|center|end"}
            display = one big number or 1-2 words (never wraps), title = headline, body = normal, caption = small grey, label = tiny uppercase
        {"type":"check","label":"...","note":"...","checked":false,"daily":false}   a to-do line the user ticks; "daily": true unticks each morning and keeps a 7-day log
        {"type":"counter","value":0,"step":1,"goal":8,"unit":"glasses","color":c,"daily":true}   the user taps − / + on the phone, no need to ask you
            "daily": true for habits counted per day: the app resets them each morning and keeps a 7-day history
        {"type":"counter","value":0,"goal":30,"unit":"minutes","label":"Exercise","color":c,"daily":true}   a duration goal: with a time
            unit ("minutes", "min", "hours", "seconds") the phone shows a stopwatch (start / pause / reset, live mm:ss, progress
            toward the goal) instead of − / +. Use it for anything measured in time (exercise, reading, study, meditation);
            keep − / + counters for things counted in units (glasses, pages, reps)
        {"type":"progress","value":0.0-1.0,"shape":"bar|ring","label":"62%","color":c}
        {"type":"chart","kind":"bar|line","values":[3,5,2],"labels":["Mon","Tue","Wed"],"color":c}   real numbers only
        {"type":"timer","minutes":25,"label":"Focus","color":c,"start":true,"log":false}   start / pause / reset; the phone notifies when it ends; "log": true counts finished sessions per day
        {"type":"timer","to":"YYYY-MM-DDTHH:mm","color":c,"style":"display|title|body"}   live time left until a date, e.g. "1d 4h"
        {"type":"input","placeholder":"Log an expense…","prompt":"Log expense"}   what the user types comes back to you as "<prompt>: <text>" with the element id
        {"type":"input","placeholder":"Add item…","add":"check"}   put it inside a list of checks: the phone adds a new check above it, no AI needed
        {"type":"button","label":"...","prompt":"...","color":c}   when tapped, the prompt comes back to you with the element id
        {"type":"button","label":"Call Mom","open":"tel:+91…","color":c}   opens on the phone instead: "tel:<number>", "https://…", "geo:0,0?q=<place>", "alarm:HH:mm"
        {"type":"metric","label":"Spent","value":"₹1,240","note":"of ₹3,000","color":c}   one headline number with a label
        {"type":"list","items":[{"title":"...","detail":"...","meta":"9:00 AM","link":"https://…"}]}   rows of events, news or schedule (max 20)
        {"type":"list","kind":"schedule","title":"...","days":[{"day":"MON","slots":[{"time":"8:00-8:50","subject":"AI"}]}]}   weekly timetable (see Photo → cards)
        {"type":"choice","question":"...","options":["A","B","C"],"answer":1,"explain":"..."}   a quiz question; the user taps an option
        At most one button with a filled colour per card. Never set text or background colours other than the names below.
        Colours c: blue, indigo, purple, pink, red, orange, yellow, green, teal, gray, black.
        Sizes n are dp, 2-24 (ring size 48-120).

        MEMORY
        <memory> holds what you know about the user. Use it to personalise everything (their name, city, college,
        goals, people, dates). When the user shares a lasting fact about themselves, add it to "remember" as a short
        third-person sentence. Don't store one-off tasks; those live on screen. Use "forget" with ids when a memory
        is wrong or the user asks.

        REMINDERS
        "remind" sends a real phone notification at a time. Use it when the user asks to be reminded, or for a timed
        task when they'd clearly want one ("exam tomorrow 9am" → remind the evening before or an hour ahead). Mention
        reminders inside the element (e.g. a caption "🔔 8:00 AM"). <reminders> lists what is scheduled; cancel with ids.
        Habits can repeat: "daily", "weekly", or "hours:N" for every N hours, N from 1 to 12 (the phone stays quiet
        from 10 PM to 8 AM).

        CALENDAR
        When the user has turned it on, <calendar> lists their real phone calendar for the next 7 days. Use it to
        plan around real events, avoid clashes and answer questions about their schedule. Only put calendar events
        on screen when they ask or it clearly helps (e.g. "plan my day").

        LIVE INFORMATION
        Never invent live data. For anything current (weather, news, facts about the world) either use your web search
        tool if you have one, or answer with ONLY {"lookup": [{"kind": "weather|news|wiki", "query": "..."}]} and the
        app will send you real results to design from. weather: a place (use the user's city from memory if they don't
        say). news: a topic, or "" for top headlines. wiki: a person, place or thing.
        {"kind":"fx","query":"100 USD INR"} gives today's exchange rate. Two more tools: {"kind":"calc","query":"1200/4"} gives exact arithmetic (use it for budgets, splits and
        totals instead of guessing), and {"kind":"calendar","from":"YYYY-MM-DD","to":"YYYY-MM-DD"} reads the
        phone calendar for any range up to a year when the user asks about dates beyond <calendar>.

        BEHAVIOUR
        - Reply in the language and style the user writes in (English, Telugu, Hindi, Hinglish, Telugu in English
          letters, …), and write the card text the same way.
        - When asked to change an element's colour, change its "tone" and the colours inside it (numbers, buttons,
          bars, chips) together, so the card stays one colour.
        - Work out dates and times from <now>. Show "Tomorrow", "Thursday", "9:00 AM" (12-hour, everywhere including
          chips), never raw ISO dates or 24-hour times.
        - <elements> shows what is on screen, including ticks and counter values. Keep them when you update.
        - To update, give the element id and the fields that change; "ui" replaces the whole tree.
        - "[New day]" messages come from the app, not the user: tidy the screen for today. Remove elements that are
          finished or in the past, update day names, re-rate importance for today, keep everything else as it is.
          The app has already reset daily counters and kept their history: don't change counters. Don't add new
          elements; keep the reply short.
        - "[Tidy]" messages come from the app when the user asks to tidy up: remove finished or past elements, merge
          duplicates, re-rate importance, and give every element its best width (update "size"). Keep the content.
        - When tidying, only remove an element if everything in it is done, its date has passed, or it duplicates
          another one. Never remove timers, trackers, counters, budgets, notes or anything still useful. Say in the
          reply what you removed, if anything.
        - In "[New day]" and "[Tidy]", cards that show live data (weather, news, scores) go stale: look the data up
          again first, then update those cards with the fresh results.
        - The user may attach a photo (a timetable, a receipt, notes, a poster, a whiteboard). Read it carefully and
          turn what matters into elements; mention dates, amounts and names exactly as written.
        - If nothing needs to go on screen (a question, small talk), just answer in "reply".
        - Things you can't do from here (send messages or emails, pay, order food, change phone settings, read
          other apps, browse logged-in sites): say so kindly in "reply" in one sentence and offer what you can do
          instead (e.g. a reminder or a checklist). Don't fake it with a card.
        - If a lookup failed or a tool is off, say so in "reply" and design without that data; never invent it.
        - Harmful, unsafe or medical-dosage questions: answer briefly and safely, suggest a professional; no card.

        - Deleting: when the user asks to delete, clear or remove cards, do it: list their ids in "remove", or use
          "remove_all" for every card on a canvas ("current" = the one on screen) or "all" for everything. The app
          asks the user to confirm and can undo, so don't refuse; just do exactly what was asked, nothing more.
        - Canvases: cards live on canvases (<canvases>; each element has a "canvas" id). New cards go on the current
          one. To move a card, update it with "canvas": "<canvas id or name>" (a new name makes a new canvas).

        Answer with ONLY a JSON object, no markdown (leave out empty lists):
        {"reply": "one short sentence",
         "create": [<element>], "update": [{"id": "...", ...}], "remove": ["<element id>"],
         "remove_all": ["all" | "current" | "<canvas id or name>"],
         "remember": ["<fact>"], "forget": ["<memory id>"],
         "remind": [{"at": "YYYY-MM-DDTHH:mm", "text": "...", "repeat": "daily" | "weekly" | "hours:N" (optional)}],
         "cancel_reminders": ["<reminder id>"],
         "add": {"tasks": [{"title": "...", "due": "YYYY-MM-DD" (optional), "priority": 0-3 (optional)}],
                 "habits": [{"name": "...", "emoji": "📚"}],
                 "countdowns": [{"title": "...", "date": "YYYY-MM-DD", "emoji": "🎓"}],
                 "expenses": [{"amount": 120, "category": "Food", "note": "Lunch", "date": "YYYY-MM-DD", "method": "Cash|UPI|Card|Bank", "income": false}]}}

        EXAMPLE (only to show the format; design each element fresh for its own content):
        "I want to drink 8 glasses of water a day"
        {"reply":"Here's your water tracker.","create":[{"size":"full","tone":"teal","importance":55,"ui":{"type":"stack","direction":"column","gap":12,"children":[
         {"type":"stack","direction":"row","spread":true,"align":"center","children":[{"type":"text","text":"Water","style":"title"},{"type":"text","text":"💧","style":"title"}]},
         {"type":"counter","value":0,"step":1,"goal":8,"unit":"glasses","color":"teal","daily":true}]}}]}
    """.trimIndent()

    /** Settings → "Test connection": one tiny request; returns normally if the key, model and URL work. */
    suspend fun testConnection(s: AppSettings) {
        call(s, listOf("user" to "Connection test from the settings screen. Reply with {\"reply\":\"OK\"} only."), null, quick = true)
    }

    /** [history] holds earlier (role, content) pairs, oldest first. */
    /** [onStatus] hears what is happening ("Checking the weather in Pune…") while the answer is being made. */
    suspend fun ask(
        s: AppSettings,
        snap: Snapshot,
        history: List<Pair<String, String>>,
        text: String,
        imageJpeg: List<ByteArray> = emptyList(),
        onStatus: (String) -> Unit = {},
        trace: (label: String, detail: String) -> Unit = { _, _ -> },
    ): Reply {
        val context = contextFor(snap, text)
        trace("Context", "${snap.elements.size} cards, ${minOf(snap.memories.size, com.aicanvas.app.data.MemoryRank.LIMIT)} of ${snap.memories.size} memories, ${snap.calendar.size} calendar lines, ${context.length} characters")
        var messages = history + ("user" to context + "\n\n" + text.ifBlank { if (imageJpeg.size > 1) "(see the photos)" else "(see the photo)" })
        // The photo belongs to this turn's message; follow-up calls in the same turn keep it.
        val image = imageJpeg.takeIf { it.isNotEmpty() }?.let { list -> Photo(history.size, list.map { Base64.getEncoder().encodeToString(it) }) }
        suspend fun call(s: AppSettings, messages: List<Pair<String, String>>) = call(s, messages, image)
        var raw = call(s, messages)
        trace("Model answered", "${raw.length} characters")
        // Tool loop: the model may ask for live data up to twice before it designs.
        for (round in 0 until MAX_LOOKUP_ROUNDS) {
            val lookups = ReplyParser.jsonIn(raw)?.optJSONArray("lookup").objects().take(4)
            if (lookups.isEmpty()) break
            onStatus(Lookup.describe(lookups))
            trace("Tool call", lookups.joinToString { it.optString("kind") + ": " + it.optString("query", it.optString("from")) })
            val results = Lookup.run(lookups, snap.calendarLookup)
            trace("Tool results", results.take(600))
            onStatus("Designing…")
            messages = messages + ("assistant" to raw) +
                ("user" to "<lookup_results>\n$results\n</lookup_results>\nNow answer in the JSON format, designing from these results.")
            raw = call(s, messages)
        }
        // Validate, and give the model exactly one chance to repair a broken answer.
        val problems = if (ReplyParser.jsonIn(raw) == null && !raw.contains('{')) emptyList() else UiContract.problems(ReplyParser.jsonIn(raw))
        if (problems.isEmpty()) trace("Validated", "Answer matches the UI contract")
        else {
            trace("Validation failed", problems.joinToString("\n"))
            onStatus("Fixing the design…")
            val fixed = call(s, messages + ("assistant" to raw) + ("user" to repairPrompt(problems)))
            val still = UiContract.problems(ReplyParser.jsonIn(fixed))
            if (still.isEmpty()) {
                trace("Repaired", "Second answer is valid")
                raw = fixed
            } else {
                trace("Repair failed", still.joinToString("\n"))
                throw AiException("The AI's design didn't pass the checks, so nothing was changed. Try again or rephrase.")
            }
        }
        // The answer can contain the user's notes, memories and calendar: only log it in debug builds.
        if (BuildConfig.DEBUG) Log.d("AICanvas", "AI answer: $raw")
        return ReplyParser.parse(raw)
    }

    private const val MAX_LOOKUP_ROUNDS = 2

    /** The message that asks the model to fix its own answer. Public for tests. */
    /**
     * Sorts cards into [categories] for Auto-organize. Returns id → category for the cards the model placed;
     * anything missing or unknown is left to the keyword classifier.
     */
    suspend fun classify(s: AppSettings, cards: Map<String, String>, categories: List<String>): Map<String, String> {
        if (cards.isEmpty()) return emptyMap()
        val list = cards.entries.joinToString("\n") { (id, text) -> "$id: ${text.replace("\n", " ")}" }
        val ask = "[Organize] This is not a design request: do not create or change cards. Put each card below into exactly one " +
            "of these categories: ${categories.joinToString(", ")}. Answer with only a JSON object mapping card id to category, " +
            "e.g. {\"ab12cd34\":\"Health\"}.\n\n$list"
        return com.aicanvas.app.data.Canvases.parseClassification(call(s, listOf("user" to ask), null), cards.keys, categories)
    }

    fun repairPrompt(problems: List<String>): String =
        "Your answer broke these rules:\n" + problems.joinToString("\n") { "- $it" } +
            "\nSend the corrected answer as one valid JSON object only, using only the listed node types."

    /** A JPEG attached to the message at [index]. */
    private class Photo(val index: Int, val base64: List<String>)

    /** [quick] = settings test: short timeouts and no retries, so it always ends within ~20 s. */
    private suspend fun call(s: AppSettings, messages: List<Pair<String, String>>, image: Photo?, quick: Boolean = false): String =
        try {
            if (s.provider == PROVIDER_ANTHROPIC) {
                try { claude(s, messages, image, quick = quick) } catch (t: Truncated) { if (quick) throw t; claude(s, messages + ("user" to SHORTER), image, MAX_RETRY_TOKENS) }
            } else openAiCompatible(s, messages, image, quick = quick)
        } catch (t: Truncated) {
            keepFinishedPart(t.partial)
        }

    /** Still cut off after the bigger retry: keep the cards that finished, or explain in plain words. Public for tests. */
    fun keepFinishedPart(partial: String): String {
        val o = Salvage.close(partial)
            ?: throw AiException("The AI's answer got cut off before any card was finished. Try again, or ask for just today's part first.")
        val note = "(The answer was long, so I kept the part that finished.)"
        o.put("reply", (o.optString("reply").trim() + " " + note).trim())
        return o.toString()
    }

    private fun contextFor(snap: Snapshot, message: String): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm"))
        val elements = JSONArray().apply { snap.elements.forEach { put(it.toJson().apply { remove("createdAt") }) } }
        val memory = JSONArray().apply { com.aicanvas.app.data.MemoryRank.pick(snap.memories, message).forEach { put(JSONObject().put("id", it.id).put("text", it.text)) } }
        val reminders = JSONArray().apply {
            snap.reminders.forEach { put(JSONObject().put("id", it.id).put("at", isoTime(it.at)).put("text", it.text).put("repeat", it.repeat)) }
        }
        val calendar = if (snap.calendar.isEmpty()) "" else "\n<calendar>\n${snap.calendar.joinToString("\n")}\n</calendar>"
        val canvases = if (snap.canvases.isEmpty()) "" else "\n<canvases current=\"${snap.currentCanvas}\">" +
            JSONArray().apply { snap.canvases.forEach { put(it.toJson()) } } + "</canvases>"
        return "<now>$now</now>\n<memory>$memory</memory>\n<reminders>$reminders</reminders>$calendar$canvases${if (snap.modules.isBlank()) "" else "\n<app_data>${snap.modules}</app_data>"}\n<elements>$elements</elements>"
    }

    private fun isoTime(millis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

    /** Cards are small JSON answers; this caps the cost of one reply (it was 16000). */
    private const val MAX_OUTPUT_TOKENS = 4096L

    /** One retry after a cut-off answer gets this much room (vision models often think out loud first). */
    private const val MAX_RETRY_TOKENS = 12000L

    /** Sent with that retry. Public for tests. */
    const val SHORTER = "Your last answer was cut off because it was too long. Send the same thing as a much more " +
        "compact design: fewer, shorter nodes (a timetable = the schedule block with one slot per class), no extra commentary, JSON only."


    // ---------------- Claude, through the official Anthropic Java SDK ----------------
    private suspend fun claude(s: AppSettings, messages: List<Pair<String, String>>, image: Photo?, cap: Long = MAX_OUTPUT_TOKENS, quick: Boolean = false): String = withContext(Dispatchers.IO) {
        val model = s.model.trim()
        val client = AnthropicOkHttpClient.builder().apiKey(cleanApiKey(s.apiKey))
            // The SDK waits and retries 429 / 5xx / overloaded with backoff (and honours Retry-After).
            .maxRetries(if (quick) 0 else com.aicanvas.app.data.Retry.MAX_ATTEMPTS)
            .timeout(java.time.Duration.ofSeconds(if (quick) TEST_TIMEOUT_S else CALL_TIMEOUT_S)).build()
        val params = messages.mapIndexed { i, (role, text) ->
            if (image != null && i == image.index) {
                MessageParam.ofUserOfContentBlockParams(
                    image.base64.map { b64 ->
                        ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                .source(Base64ImageSource.builder().data(b64).mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build())
                                .build()
                        )
                    } + listOf(
                        ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()),
                    )
                )
            } else if (role == "user") MessageParam.ofUser(text) else MessageParam.ofAssistant(text)
        }.toMutableList()
        val out = StringBuilder()
        try {
            // Web search runs on Anthropic's side; a long search can pause the turn, so continue a few times.
            for (step in 0 until 4) {
                val b = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(cap)
                    .system(SYSTEM)
                    .messages(params)
                if (Regex("claude-(opus-5|opus-4-[678]|sonnet-5|sonnet-4-6)").containsMatchIn(model)) {
                    b.addTool(WebSearchTool20260209.builder().maxUses(3L).build())
                } else {
                    b.addTool(WebSearchTool20250305.builder().maxUses(3L).build())
                }
                if (Regex("claude-(opus|sonnet|haiku|fable)-5").containsMatchIn(model)) {
                    // Designing one small element is quick work: low effort keeps the screen responsive.
                    b.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                }
                if (Regex("claude-(opus-5|sonnet-5-5|fable-5-1)").containsMatchIn(model)) {
                    // If a safety classifier declines, the API retries on a suitable fallback model instead of stopping.
                    b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
                val response = client.messages().create(b.build())
                response.content().forEach { block -> block.text().ifPresent { out.append(it.text()) } }
                when (response.stopReason().orElse(null)) {
                    StopReason.PAUSE_TURN -> params += response.toParam()
                    StopReason.REFUSAL -> throw AiException("Claude declined that request.")
                    StopReason.MAX_TOKENS -> throw Truncated(out.toString())
                    else -> return@withContext out.toString()
                }
            }
            out.toString()
        } finally {
            client.close()
        }
    }

    // ---------------- Any OpenAI-compatible API (OpenAI, Gemini, Groq, OpenRouter, Ollama) ----------------
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        // Never let a redirect move the request (and the API key) from https to plain http.
        .followSslRedirects(false)
        .build()
    const val TEST_TIMEOUT_S = 20L
    const val CALL_TIMEOUT_S = 150L
    /** The settings test gives up after 20 s in total. */
    private val quickHttp by lazy {
        http.newBuilder().connectTimeout(TEST_TIMEOUT_S, TimeUnit.SECONDS).readTimeout(TEST_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(TEST_TIMEOUT_S, TimeUnit.SECONDS).build()
    }

    private suspend fun openAiCompatible(s: AppSettings, messages: List<Pair<String, String>>, image: Photo?, cap: Long = MAX_OUTPUT_TOKENS, quick: Boolean = false): String = withContext(Dispatchers.IO) {
        BaseUrls.problem(s.baseUrl)?.let { throw AiException(it) }
        val base = Gemini.normalize(s.baseUrl).ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val client = if (quick) quickHttp else http
        val arr = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM))
        messages.forEachIndexed { i, (role, text) ->
            val content: Any = if (image != null && i == image.index) {
                JSONArray().put(JSONObject().put("type", "text").put("text", text)).also { a ->
                    image.base64.forEach { a.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) }
                }
            } else text
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject().put("model", s.model.trim()).put("messages", arr).put("max_tokens", cap)
        fun requestFor(b: JSONObject) = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer ${cleanApiKey(s.apiKey)}")
            .post(b.toString().toRequestBody("application/json".toMediaType()))
            .build()
        var request = requestFor(body)
        // Free models are often busy or rate-limited: wait and try again a couple of times before giving up.
        var response = client.newCall(request).execute()
        var attempt = 0
        while (!quick && com.aicanvas.app.data.Retry.worthRetry(response.code) && attempt < com.aicanvas.app.data.Retry.MAX_ATTEMPTS) {
            val wait = com.aicanvas.app.data.Retry.delayMs(++attempt, response.header("Retry-After"))
            response.close()
            delay(wait)
            response = client.newCall(request).execute()
        }
        if (response.code == 400) {
            // Newer OpenAI models only accept "max_completion_tokens": retry once with that name.
            val errorText = response.peekBody(64_000).string()
            if (ChatCompletion.wantsMaxCompletionTokens(response.code, errorText)) {
                response.close()
                body.remove("max_tokens")
                request = requestFor(body.put("max_completion_tokens", cap))
                response = client.newCall(request).execute()
            }
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = apiErrorMessage(text)
                throw AiException(com.aicanvas.app.data.Errors.http(resp.code, msg?.ifBlank { null } ?: text.take(300)))
            }
            try {
                ChatCompletion.content(text)
            } catch (t: Truncated) {
                if (cap >= MAX_RETRY_TOKENS || quick) throw t
                // Cut off at the cap: try once more with room to finish and a nudge to keep it compact.
                openAiCompatible(s, messages + ("user" to SHORTER), image, MAX_RETRY_TOKENS)
            }
        }
    }

    /** Turns provider exceptions into a sentence the user can act on. */
    fun friendlyError(e: Throwable): String = when (e) {
        is AiException -> e.message!!
        is UnauthorizedException -> com.aicanvas.app.data.Errors.http(401, null)
        is PermissionDeniedException -> com.aicanvas.app.data.Errors.http(403, null)
        is NotFoundException -> com.aicanvas.app.data.Errors.http(404, null)
        is RateLimitException -> com.aicanvas.app.data.Errors.http(429, null)
        is AnthropicServiceException -> com.aicanvas.app.data.Errors.http(e.statusCode(), e.message)
        is AnthropicIoException, is java.net.UnknownHostException, is java.net.ConnectException ->
            "Can't reach the AI. Check your internet connection (or that your local server is running)."
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException, is kotlinx.coroutines.TimeoutCancellationException ->
            "Timed out: no answer from the AI. Check the Base URL and your connection, then try again."
        is javax.net.ssl.SSLException -> "Secure connection failed. Check the Base URL."
        is java.io.IOException -> "Network problem: ${e.message ?: "couldn't reach the AI"}."
        is OutOfMemoryError -> "That was too big for the phone's memory. Try a smaller photo."
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** The four card widths, or null for anything else. */
fun sizeOf(s: String): String? = s.lowercase().replace("-", "").replace("_", "").let {
    when (it) {
        "third", "onethird" -> "third"
        "half" -> "half"
        "twothirds" -> "twothirds"
        "full" -> "full"
        else -> null
    }
}

/** Removes a pasted "Bearer " prefix, spaces, line breaks and invisible characters from a key. */
fun cleanApiKey(raw: String): String =
    raw.trim().removePrefix("Bearer ").removePrefix("bearer ").filter { it in '!'..'~' }
