package com.aicanvas.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ExpensesTest {
    private val today = LocalDate.of(2026, 10, 31) // a Saturday

    private fun e(amount: Double, date: String, cat: String = "Food", income: Boolean = false) =
        Expense(amount = amount, category = cat, date = date, income = income)

    @Test fun parseQuickReadsAmountNoteAndCategory() {
        val x = Expenses.parseQuick("spent 120 on lunch", today = today)!!
        assertEquals(120.0, x.amount, 0.0)
        assertEquals("Food", x.category)
        assertEquals("Lunch", x.note)
        assertEquals("2026-10-31", x.date)
        assertFalse(x.income)
    }

    @Test fun parseQuickHandlesSymbolsDecimalsMethodYesterdayAndIncome() {
        val a = Expenses.parseQuick("₹45.50 uber upi yesterday", today = today)!!
        assertEquals(45.5, a.amount, 0.001); assertEquals("Transport", a.category); assertEquals("UPI", a.method); assertEquals("2026-10-30", a.date)
        val b = Expenses.parseQuick("salary 50000", today = today)!!
        assertTrue(b.income); assertEquals("Income", b.category)
        assertNull(Expenses.parseQuick("lunch with friends"))
        assertEquals("Other", Expenses.parseQuick("80 misc stuff", today = today)!!.category)
    }

    @Test fun totalsSplitTodayWeekMonthAndIgnoreIncome() {
        val list = listOf(e(100.0, "2026-10-31"), e(50.0, "2026-10-26"), e(30.0, "2026-10-25"), e(10.0, "2026-09-30"), e(999.0, "2026-10-31", "Income", true))
        val t = Expenses.totals(list, today)
        assertEquals(100.0, t.today, 0.0)
        assertEquals(150.0, t.week, 0.0) // Monday 26th onwards
        assertEquals(180.0, t.month, 0.0)
        assertEquals(999.0, t.monthIncome, 0.0)
    }

    @Test fun byCategoryAndTrend() {
        val list = listOf(e(100.0, "2026-10-31"), e(40.0, "2026-10-30", "Bills"), e(20.0, "2026-10-30"))
        assertEquals(listOf("Food" to 120.0, "Bills" to 40.0), Expenses.byCategory(list, today))
        val tr = Expenses.trend(list, today, 3)
        assertEquals(listOf(0.0, 60.0, 100.0), tr.map { it.second })
        assertEquals(today, tr.last().first)
    }

    @Test fun budgetUsed() {
        assertNull(Expenses.budgetUsed(100.0, 0.0))
        assertEquals(1.25, Expenses.budgetUsed(500.0, 400.0)!!, 0.0001)
    }

    @Test fun filterBySearchAndCategoryNewestFirst() {
        val list = listOf(e(10.0, "2026-10-01").copy(note = "Coffee"), e(20.0, "2026-10-05", "Bills").copy(note = "Wifi"), e(5.0, "2026-10-09").copy(note = "Tea"))
        assertEquals(listOf("Tea", "Coffee"), Expenses.filter(list, "", "Food").map { it.note })
        assertEquals(listOf("Wifi"), Expenses.filter(list, "wif", null).map { it.note })
    }

    @Test fun recurringTemplatesCopyOnEachDueDateOnce() {
        val tpl = Expense(id = "t", amount = 500.0, category = "Bills", note = "Rent", date = "2026-08-31", repeat = "monthly")
        val once = Expenses.materialize(listOf(tpl), today)
        val copies = once.filter { it.fromRepeat == "t" }
        assertEquals(listOf("2026-08-31", "2026-09-30", "2026-10-30"), copies.map { it.date }.sorted())
        // Running again adds nothing; templates aren't counted as spending.
        val twice = Expenses.materialize(once, today)
        assertEquals(once.size, twice.size)
        assertEquals(500.0, Expenses.totals(twice, today).month, 0.0)
    }

    @Test fun csvEscapesAndHasHeader() {
        val csv = Expenses.csv(listOf(e(12.5, "2026-10-31").copy(note = "Tea, \"masala\"")))
        assertTrue(csv.startsWith("date,type,amount,category,method,note,repeat\n"))
        assertTrue(csv.contains("2026-10-31,expense,12.50,Food,Cash,\"Tea, \"\"masala\"\"\",none"))
    }

    @Test fun jsonRoundTripAndSettings() {
        val x = e(42.0, "2026-10-31").copy(note = "n", method = "Card", repeat = "weekly")
        assertEquals(x, Expense.fromJson(x.toJson()))
        assertNull(Expense.fromJson(JSONObject().put("amount", -3)))
        val s = ExpenseSettings(currency = "$", budget = 900.0)
        assertEquals(s, ExpenseSettings.fromJson(s.toJson().toString()))
        assertEquals("₹", ExpenseSettings.fromJson(null).currency)
    }

    @Test fun migratesEntriesFromAnOldExpenseCardButNotTotals() {
        val ui = JSONObject().put("type", "stack").put("children", JSONArray()
            .put(JSONObject().put("type", "text").put("style", "title").put("text", "Expense log"))
            .put(JSONObject().put("type", "list").put("items", JSONArray().put("Lunch ₹120").put("Uber 80").put("Total 200"))))
        val found = Expenses.fromCard(ui, java.time.ZonedDateTime.of(2026, 10, 2, 12, 0, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(listOf(120.0, 80.0), found.map { it.amount })
        assertEquals(listOf("Food", "Transport"), found.map { it.category })
        assertTrue(found.all { it.date == "2026-10-02" })
        assertTrue(Expenses.fromCard(JSONObject().put("type", "text").put("text", "Water 8 glasses"), 0).isEmpty())
    }
}
