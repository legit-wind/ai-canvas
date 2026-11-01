package com.aicanvas.app.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ToolsTest {
    @Test fun splitBill() = assertEquals(300.0, Calc.eval("1200 / 4")!!, 1e-9)
    @Test fun precedenceAndBrackets() = assertEquals(14.0, Calc.eval("2 + 3 * 4")!!, 1e-9).also { assertEquals(20.0, Calc.eval("(2+3)*4")!!, 1e-9) }
    @Test fun percentAndRupees() = assertEquals(450.0, Calc.eval("₹3,000 * 15%")!!, 1e-9)
    @Test fun symbols() = assertEquals(6.0, Calc.eval("12 ÷ 4 × 2")!!, 1e-9)
    @Test fun rejectsJunk() { assertNull(Calc.eval("2 + abc")); assertNull(Calc.eval("1/0")) }
    @Test fun formatting() { assertEquals("300", Calc.format(300.0)); assertEquals("33.33", Calc.format(100.0 / 3)) }
    @Test fun calendarRangeCappedAtAYear() {
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(d.plusYears(1), PhoneCalendar.clampRange(d, d.plusYears(5)).second)
        assertEquals(d.plusDays(1), PhoneCalendar.clampRange(d, d.minusDays(3)).second)
    }
}
