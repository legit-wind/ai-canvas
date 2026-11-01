package com.aicanvas.app.data

/**
 * A one-tap idea: shown on the empty screen and in the chat. [key] is a word that means it's already on screen.
 * [needs] is what the idea uses besides the AI: "photo" opens the photo picker first, "calendar" asks for
 * calendar access first, "" sends right away.
 */
data class Starter(
    val emoji: String,
    val title: String,
    val prompt: String,
    val tone: String,
    val key: String,
    val needs: String = "",
    /** One line: what makes this use case different (shown nowhere; kept for the docs and tests). */
    val shows: String = "",
)

/** Ready-made use cases, each exercising a different capability. Pure data, unit-tested. */
object Starters {
    val ALL = listOf(
        Starter("📸", "Photo → cards", "Turn this photo into cards (timetable, receipt or notes): keep dates, times and amounts exactly as written", "blue", "📸", needs = "photo", shows = "vision: reads a photo"),
        Starter("🧾", "Split a bill", "Split ₹2,340 dinner between me, Ravi, Asha and Kiran; Ravi already paid ₹500. Who owes whom?", "amber", "owes", shows = "calc tool, exact amounts"),
        Starter("⏳", "Exam countdown + plan", "My exams start on the 1st of next month. Countdown plus a day-by-day study plan, and remind me each evening at 7", "rose", "exam", shows = "live countdown + daily reminder + plan"),
        Starter("💊", "Medicine schedule", "Medicine at 9 AM and 9 PM daily: remind me, let me tick each dose, and keep a 7-day taken log", "rose", "dose", shows = "repeating reminders + daily ticks with log"),
        Starter("🌦️", "Weather-aware day", "Plan my day around my calendar and today's weather where I am", "blue", "plan", needs = "calendar", shows = "calendar + weather together"),
        Starter("📰", "News digest", "A 5-headline news digest on India and tech that refreshes when I tidy", "violet", "headline", shows = "live news, refreshed on tidy"),
        Starter("🧠", "Quick-learn quiz", "Teach me about black holes from Wikipedia, then quiz me with 3 questions and keep score", "violet", "score", shows = "wiki lookup + quiz with local score"),
        Starter("💱", "Converter", "A converter card: currency at today's rate (USD ↔ INR) and units I type, like 5 km in miles", "teal", "convert", shows = "live exchange rate + calc, typed input"),
        Starter("📊", "Expense log", "An expense log where I type things like \"lunch 120 food\"; show today's total and a chart by category", "amber", "category", shows = "typed entries → category chart"),
        Starter("🔥", "Habit streaks", "Daily habits with streaks: 8 glasses of water and 30 minutes of exercise, with 7-day history", "green", "streak", shows = "daily counters, streak, 7-day bars"),
        Starter("🍅", "Focus sessions", "A 25-minute pomodoro that logs how many sessions I finish each day", "rose", "session", shows = "timer + local session log"),
        Starter("🧳", "Trip packing", "I'm going to Manali for 3 days next week: packing checklist based on the weather there", "teal", "packing", shows = "weather lookup shapes a checklist"),
        Starter("🎂", "Birthdays from calendar", "Find birthdays and events in my calendar for the next 3 months and show countdowns", "rose", "birthday", needs = "calendar", shows = "calendar range lookup + countdowns"),
        Starter("⚖️", "Decision helper", "Help me decide: laptop now or wait 3 months. Pros, cons and a final pick", "violet", "pros", shows = "pros/cons + choice"),
        Starter("🪪", "What you know about me", "Show a card of everything you remember about me, so I can fix anything wrong", "blue", "remember", shows = "memory, editable"),
        Starter("📔", "Journal & mood", "A daily journal: pick my mood, write a line, and chart my mood this week", "violet", "journal", shows = "choice + input → mood chart"),
        Starter("📍", "Places & maps", "Quick map links for my college, the nearest hospital and a good café nearby", "teal", "geo:", shows = "map buttons open Maps"),
        Starter("📞", "Quick call", "Quick-call card for Mom and my best friend (I'll tell you the numbers)", "green", "tel:", shows = "call buttons open the dialer"),
    )

    /** Up to [max] ideas that aren't already on screen, starting at a different place each hour. */
    fun suggest(screenText: String, hour: Int, max: Int = 6): List<Starter> {
        val screen = screenText.lowercase()
        val fresh = ALL.filter { it.key !in screen }
        if (fresh.isEmpty()) return emptyList()
        val start = (hour * 5) % fresh.size
        return (fresh.drop(start) + fresh.take(start)).take(max)
    }
}
