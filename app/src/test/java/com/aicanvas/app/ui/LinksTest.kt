package com.aicanvas.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test fun allowsSafeLinks() {
        assertEquals(Links.Kind.WEB, Links.kindOf("https://example.com/a?b=1"))
        assertEquals(Links.Kind.TEL, Links.kindOf("tel:+91 98765-43210"))
        assertEquals(Links.Kind.GEO, Links.kindOf("geo:0,0?q=library"))
        assertEquals(Links.Kind.ALARM, Links.kindOf("alarm:07:30"))
        assertEquals(7 to 30, Links.alarmTime("alarm:07:30"))
    }

    @Test fun blocksEverythingElse() {
        listOf(
            "http://example.com", "intent://x#Intent;component=a/b;end", "content://x/y", "file:///sdcard/a",
            "javascript:alert(1)", "sms:123", "tel:123;ussd", "alarm:25:00", "https://", "market://details?id=x", "",
        ).forEach { assertNull(it, Links.kindOf(it)) }
    }
}
