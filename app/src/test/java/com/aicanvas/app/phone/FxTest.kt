package com.aicanvas.app.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FxTest {
    @Test fun codes() = assertEquals(Triple(100.0, "USD", "INR"), Fx.parse("100 USD INR"))
    @Test fun words() = assertEquals(Triple(500.0, "INR", "USD"), Fx.parse("₹500 in dollars"))
    @Test fun commasAndTo() = assertEquals(Triple(1500.0, "EUR", "INR"), Fx.parse("1,500 eur to inr"))
    @Test fun defaultsToOne() = assertEquals(Triple(1.0, "GBP", "INR"), Fx.parse("pound rupee"))
    @Test fun needsTwo() = assertNull(Fx.parse("100 USD"))
}
