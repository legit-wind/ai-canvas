package com.aicanvas.app.ai

import com.aicanvas.app.data.Errors
import org.junit.Assert.*
import org.junit.Test

class GeminiTest {
    @Test fun normalizesGeminiUrl() {
        assertEquals(Gemini.BASE_URL, Gemini.normalize("https://generativelanguage.googleapis.com/v1beta/"))
        assertEquals(Gemini.BASE_URL, Gemini.normalize("https://generativelanguage.googleapis.com"))
        assertEquals(Gemini.BASE_URL, Gemini.normalize(Gemini.BASE_URL))
        assertEquals("https://api.openai.com/v1", Gemini.normalize("https://api.openai.com/v1"))
        assertNotNull(Gemini.hint("https://generativelanguage.googleapis.com/v1beta/"))
        assertNull(Gemini.hint(Gemini.BASE_URL))
        assertNull(Gemini.hint("https://openrouter.ai/api/v1"))
    }

    @Test fun parsesErrorBodies() {
        assertEquals("API key not valid", apiErrorMessage("""[{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}]"""))
        assertEquals("bad model", apiErrorMessage("""{"error":{"message":"bad model"}}"""))
        assertEquals("NOT_FOUND", apiErrorMessage("""[{"error":{"code":404,"status":"NOT_FOUND"}}]"""))
        assertNull(apiErrorMessage("<html>404</html>"))
    }

    @Test fun clearMessages() {
        assertTrue(Errors.http(404, null).contains("Base URL"))
        assertTrue(Errors.http(401, null).contains("key"))
        assertTrue(Errors.http(429, null).contains("rate limit"))
        assertTrue(Ai.friendlyError(java.net.SocketTimeoutException()).startsWith("Timed out"))
        assertTrue(Ai.friendlyError(java.net.UnknownHostException()).contains("internet"))
    }
}
