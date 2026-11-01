package com.aicanvas.app.data

import org.junit.Assert.*
import org.junit.Test

class ModulesTest {
    @Test fun uiRoundTripsKind() {
        assertEquals("habits", Modules.kindOf(Modules.ui("habits")))
        assertNull(Modules.kindOf(org.json.JSONObject().put("type", "text")))
        assertTrue(CardSummary.isSmall(Modules.ui("tasks")))
        assertEquals(Modules.title("tasks"), CardSummary.fold(Modules.ui("tasks")).title)
    }

    @Test fun missingOnlyForKindsWithDataNoCardAndNotHidden() {
        val data = mapOf("tasks" to true, "habits" to true, "countdowns" to false, "expenses" to true)
        assertEquals(listOf("habits", "expenses"), Modules.missing(data, listOf(Modules.ui("tasks")), emptySet(), true))
        assertEquals(listOf("habits"), Modules.missing(data, listOf(Modules.ui("tasks")), setOf("expenses"), true))
        assertTrue(Modules.missing(data, emptyList(), emptySet(), enabled = false).isEmpty())
    }
}
