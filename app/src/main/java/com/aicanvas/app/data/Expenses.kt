package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One money entry. [income] entries count towards income, never towards spending or the budget. */
data class Expense(
    val id: String = newId(),
    val amount: Double,
    val category: String = "Other",
    val note: String = "",
    val date: String = LocalDate.now().toString(),
    val method: String = "Cash",
    val income: Boolean = false,
    /** "none", "daily", "weekly" or "monthly": a recurring entry is a template the app copies on each due date. */
    val repeat: String = "none",
    /** For a recurring template: the last date a copy was made (yyyy-MM-dd). */
    val lastCopied: String = "",
    /** Set on copies made from a recurring template. */
    val fromRepeat: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("amount", amount).put("category", category).put("note", note)
        .put("date", date).put("method", method).put("income", income).put("repeat", repeat).put("lastCopied", lastCopied)
        .put("fromRepeat", fromRepeat).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): Expense? {
            val amount = o.optDouble("amount", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return null
            return Expense(
                id = o.optString("id").ifEmpty { newId() }, amount = amount,
                category = o.optString("category").ifBlank { "Other" }, note = o.optString("note"),
                date = o.optString("date").takeIf { Tasks.parseDate(it) != null } ?: LocalDate.now().toString(),
                method = o.optString("method").ifBlank { "Cash" }, income = o.optBoolean("income"),
                repeat = o.optString("repeat", "none").takeIf { it in Expenses.REPEATS } ?: "none",
                lastCopied = o.optString("lastCopied"), fromRepeat = o.optString("fromRepeat"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}

/** A spending category with an emoji icon and a palette colour name (see Tones). */
data class ExpenseCategory(val name: String, val emoji: String, val color: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("emoji", emoji).put("color", color)
    companion object {
        fun fromJson(o: JSONObject) = o.optString("name").trim().takeIf { it.isNotEmpty() }?.let {
            ExpenseCategory(it, o.optString("emoji").ifBlank { "•" }, o.optString("color").ifBlank { "blue" })
        }
    }
}

/** Expense tracker settings: currency symbol, monthly budget (0 = none) and categories. */
data class ExpenseSettings(
    val currency: String = "₹",
    val budget: Double = 0.0,
    val categories: List<ExpenseCategory> = Expenses.DEFAULT_CATEGORIES,
) {
    fun toJson(): JSONObject = JSONObject().put("currency", currency).put("budget", budget)
        .put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
    companion object {
        fun fromJson(raw: String?): ExpenseSettings {
            val o = raw?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return ExpenseSettings()
            val cats = o.optJSONArray("categories")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(ExpenseCategory::fromJson) } }
            return ExpenseSettings(o.optString("currency").ifBlank { "₹" }, o.optDouble("budget", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                cats?.ifEmpty { null } ?: Expenses.DEFAULT_CATEGORIES)
        }
    }
}

data class Totals(val today: Double, val week: Double, val month: Double, val monthIncome: Double)

/** Pure expense maths, kept here so it is unit-tested. */
object Expenses {
    val REPEATS = listOf("none", "daily", "weekly", "monthly")
    val METHODS = listOf("Cash", "UPI", "Card", "Bank")
    val DEFAULT_CATEGORIES = listOf(
        ExpenseCategory("Food", "🍽️", "amber"),
        ExpenseCategory("Transport", "🚌", "blue"),
        ExpenseCategory("Shopping", "🛍️", "rose"),
        ExpenseCategory("Bills", "💡", "violet"),
        ExpenseCategory("Health", "💊", "green"),
        ExpenseCategory("Fun", "🎬", "teal"),
        ExpenseCategory("Education", "📚", "blue"),
        ExpenseCategory("Other", "•", "none"),
    )

    private val KEYWORDS = mapOf(
        "Food" to listOf("food", "lunch", "dinner", "breakfast", "coffee", "tea", "snack", "swiggy", "zomato", "grocer", "restaurant", "pizza", "meal"),
        "Transport" to listOf("uber", "ola", "auto", "bus", "metro", "train", "petrol", "fuel", "cab", "taxi", "rapido", "parking", "transport"),
        "Shopping" to listOf("amazon", "flipkart", "clothes", "shoes", "shopping", "myntra", "gift"),
        "Bills" to listOf("rent", "electricity", "bill", "recharge", "wifi", "internet", "phone", "water bill", "emi", "subscription"),
        "Health" to listOf("medicine", "doctor", "pharmacy", "gym", "hospital", "health"),
        "Fun" to listOf("movie", "netflix", "game", "party", "concert", "fun", "trip"),
        "Education" to listOf("book", "course", "fees", "tuition", "exam", "stationery", "college", "school"),
    )

    /** The category a note most likely belongs to, among [categories]; "Other" when nothing matches. */
    fun guessCategory(text: String, categories: List<ExpenseCategory> = DEFAULT_CATEGORIES): String {
        val t = text.lowercase()
        categories.firstOrNull { c -> Regex("\\b${Regex.escape(c.name.lowercase())}\\b").containsMatchIn(t) }?.let { return it.name }
        val hit = KEYWORDS.entries.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.key
        return hit?.takeIf { h -> categories.any { it.name == h } } ?: categories.firstOrNull { it.name == "Other" }?.name ?: "Other"
    }

    /**
     * Reads quick entries like "lunch 120", "120 lunch food", "spent ₹45.50 on coffee", "salary 50000 income".
     * Null when there's no amount.
     */
    fun parseQuick(text: String, categories: List<ExpenseCategory> = DEFAULT_CATEGORIES, today: LocalDate = LocalDate.now()): Expense? {
        val t = text.trim()
        val m = Regex("""(?:₹|rs\.?|inr|\$)?\s*(\d+(?:[.,]\d{1,2})?)\s*(?:₹|rs|rupees|inr)?""", RegexOption.IGNORE_CASE).find(t) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val rest = t.replaceRange(m.range, " ").replace(Regex("\\b(spent|paid|on|for|rs|inr|rupees)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        val income = Regex("\\b(income|salary|received|got paid|refund)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)
        val date = if (Regex("\\byesterday\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) today.minusDays(1) else today
        val method = METHODS.firstOrNull { Regex("\\b${it}\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) } ?: "Cash"
        val note = rest.replace(Regex("\\b(yesterday|income|upi|cash|card|bank)\\b", RegexOption.IGNORE_CASE), "").replace(Regex("\\s+"), " ").trim()
        return Expense(amount = amount, category = if (income) "Income" else guessCategory(t, categories), note = note.replaceFirstChar { it.uppercase() },
            date = date.toString(), method = method, income = income)
    }

    /** Entries actually counted (recurring templates are not themselves spending; their copies are). */
    fun entries(all: List<Expense>) = all.filter { it.repeat == "none" }

    fun totals(all: List<Expense>, today: LocalDate): Totals {
        val list = entries(all)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun sum(from: LocalDate, income: Boolean = false) = list.filter { it.income == income }.filter { e ->
            Tasks.parseDate(e.date)?.let { !it.isBefore(from) && !it.isAfter(today) } == true
        }.sumOf { it.amount }
        return Totals(sum(today), sum(weekStart), sum(today.withDayOfMonth(1)), sum(today.withDayOfMonth(1), income = true))
    }

    /** Spending this month by category, biggest first. */
    fun byCategory(all: List<Expense>, today: LocalDate): List<Pair<String, Double>> =
        entries(all).filter { !it.income && Tasks.parseDate(it.date)?.let { d -> d.year == today.year && d.month == today.month && !d.isAfter(today) } == true }
            .groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    /** Spending per day for the last [days] days, oldest first. */
    fun trend(all: List<Expense>, today: LocalDate, days: Int = 14): List<Pair<LocalDate, Double>> {
        val list = entries(all).filter { !it.income }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to list.filter { it.date == d.toString() }.sumOf { it.amount }
        }
    }

    /** Fraction of the monthly budget spent (may pass 1), or null with no budget. */
    fun budgetUsed(spent: Double, budget: Double): Double? = if (budget > 0) spent / budget else null

    fun filter(all: List<Expense>, query: String, category: String?): List<Expense> {
        val q = query.trim().lowercase()
        return entries(all).filter { e ->
            (category == null || e.category == category) &&
                (q.isEmpty() || listOf(e.note, e.category, e.method, money(e.amount)).any { it.lowercase().contains(q) })
        }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
    }

    /** Copies due recurring templates up to [today]. Returns the new list (templates updated + copies added). */
    fun materialize(all: List<Expense>, today: LocalDate): List<Expense> {
        val out = all.toMutableList()
        all.forEachIndexed { i, t ->
            if (t.repeat == "none") return@forEachIndexed
            var next = Tasks.parseDate(t.lastCopied)?.let { step(it, t.repeat) } ?: Tasks.parseDate(t.date) ?: return@forEachIndexed
            var last = t.lastCopied
            var guard = 0
            while (!next.isAfter(today) && guard++ < 400) {
                out += t.copy(id = newId(), date = next.toString(), repeat = "none", lastCopied = "", fromRepeat = t.id, createdAt = System.currentTimeMillis())
                last = next.toString(); next = step(next, t.repeat)
            }
            out[i] = t.copy(lastCopied = last)
        }
        return out
    }

    private fun step(d: LocalDate, repeat: String): LocalDate = when (repeat) {
        "daily" -> d.plusDays(1); "weekly" -> d.plusWeeks(1); else -> d.plusMonths(1)
    }

    fun money(v: Double): String = if (v == Math.floor(v)) "%,.0f".format(v) else "%,.2f".format(v)

    fun csv(all: List<Expense>): String {
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        return "date,type,amount,category,method,note,repeat\n" + all.sortedBy { it.date }.joinToString("") { e ->
            listOf(e.date, if (e.income) "income" else "expense", "%.2f".format(e.amount), q(e.category), q(e.method), q(e.note), e.repeat).joinToString(",") + "\n"
        }
    }

    /**
     * Pulls entries out of an AI-made expense-log card (lines like "Lunch ₹120" or "120 lunch food"), so existing
     * logs join the tracker. Only cards whose text mentions expenses/spending are read; totals aren't imported.
     */
    fun fromCard(ui: JSONObject, createdAt: Long, categories: List<ExpenseCategory> = DEFAULT_CATEGORIES): List<Expense> {
        val text = Canvases.textOf(ui).lowercase()
        if (!Regex("expense|spent|spending").containsMatchIn(text)) return emptyList()
        val date = java.time.Instant.ofEpochMilli(createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val out = mutableListOf<Expense>()
        fun walk(n: JSONObject) {
            val type = n.optString("type")
            val lines = when (type) {
                "list" -> n.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.opt(it).let { x -> if (x is JSONObject) x.optString("text") + " " + x.optString("value") else x.toString() } } } ?: emptyList()
                "row" -> listOf(Canvases.textOf(n))
                else -> emptyList()
            }
            lines.forEach { line ->
                if (Regex("total|budget|left|average|today", RegexOption.IGNORE_CASE).containsMatchIn(line)) return@forEach
                parseQuick(line, categories, date)?.takeIf { it.note.isNotBlank() }?.let { out += it.copy(date = date.toString()) }
            }
            if (type != "row") n.optJSONArray("children")?.let { c -> for (i in 0 until c.length()) c.optJSONObject(i)?.let(::walk) }
        }
        walk(ui)
        return out
    }
}
