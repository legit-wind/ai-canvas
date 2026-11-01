package com.aicanvas.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TasksTest {
    private val today = LocalDate.of(2026, 10, 31)
    private fun t(title: String, due: String? = null, p: Int = 2, done: Boolean = false, tags: List<String> = emptyList()) =
        TaskItem(title = title, due = due, priority = p, done = done, tags = tags, doneAt = if (done) 1L else null)

    private val list = listOf(
        t("later", "2026-11-05"), t("overdue", "2026-10-20"), t("today", "2026-10-31", p = 3),
        t("urgent nodate", p = 1), t("finished", done = true, tags = listOf("exam")),
    )

    @Test fun openViewOrdersByDueThenNoDate() =
        assertEquals(listOf("overdue", "today", "later", "urgent nodate"), Tasks.view(list, "all", "", today).map { it.title })

    @Test fun todayIncludesOverdue() = assertEquals(listOf("overdue", "today"), Tasks.view(list, "today", "", today).map { it.title })
    @Test fun upcomingOnlyFuture() = assertEquals(listOf("later"), Tasks.view(list, "upcoming", "", today).map { it.title })
    @Test fun priorityFirst() = assertEquals("urgent nodate", Tasks.view(list, "priority", "", today).first().title)
    @Test fun completedView() = assertEquals(listOf("finished"), Tasks.view(list, "completed", "", today).map { it.title })
    @Test fun searchMatchesTags() = assertEquals(listOf("finished"), Tasks.view(list, "completed", "EXA", today).map { it.title })
    @Test fun overdueFlag() { assertTrue(Tasks.isOverdue(list[1], today)); assertFalse(Tasks.isOverdue(list[2], today)) }

    @Test fun repeatingMovesToNextDateAndResetsSteps() {
        val r = TaskItem(title = "gym", due = "2026-10-31", repeat = "weekly", subtasks = listOf(Subtask("a", true)))
        val n = Tasks.complete(r)
        assertFalse(n.done); assertEquals("2026-11-07", n.due); assertFalse(n.subtasks[0].done)
        assertEquals("2026-11-30", Tasks.complete(r.copy(repeat = "monthly", due = "2026-10-30")).due)
    }

    @Test fun completeAndUndo() {
        val d = Tasks.complete(t("x"), now = 5)
        assertTrue(d.done); assertEquals(5L, d.doneAt)
        assertFalse(Tasks.complete(d).done)
    }

    @Test fun jsonRoundTripAndBadData() {
        val a = TaskItem(title = "Read ch 4", notes = "n", due = "2026-11-02", priority = 1, category = "Study",
            tags = listOf("bio"), subtasks = listOf(Subtask("s", true)), repeat = "daily")
        assertEquals(a, TaskItem.fromJson(a.toJson()))
        assertNull(TaskItem.fromJson(org.json.JSONObject("{\"title\":\"  \"}")))
        val bad = TaskItem.fromJson(org.json.JSONObject("{\"title\":\"x\",\"due\":\"soon\",\"priority\":9,\"repeat\":\"hourly\"}"))!!
        assertNull(bad.due); assertEquals(3, bad.priority); assertEquals("none", bad.repeat)
    }

    @Test fun tags() = assertEquals(listOf("a", "b c"), Tasks.parseTags("a, #b c,, a"))
}
