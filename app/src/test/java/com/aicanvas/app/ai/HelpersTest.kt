package com.aicanvas.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelpersTest {
    @Test fun sizes() {
        assertEquals("twothirds", sizeOf("two-thirds"))
        assertEquals("third", sizeOf("One_Third"))
        assertNull(sizeOf("huge"))
    }

    @Test fun cleansPastedKeys() {
        assertEquals("sk-abc123", cleanApiKey("  Bearer sk-abc\u200B123\n"))
    }
}
