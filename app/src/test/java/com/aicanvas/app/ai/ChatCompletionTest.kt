package com.aicanvas.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionTest {
    @Test fun readsContentAndStripsThinking() {
        val body = """{"choices":[{"message":{"content":"<think>hmm</think>{\"reply\":\"hi\"}"}}]}"""
        assertEquals("""{"reply":"hi"}""", ChatCompletion.content(body))
    }

    @Test fun friendlyErrorsForBadBodies() {
        listOf("<html>502</html>", "{}", """{"choices":[]}""", """{"choices":[{"message":{"content":null}}]}""", """{"error":{"message":"nope"}}""")
            .forEach { body ->
                val e = runCatching { ChatCompletion.content(body) }.exceptionOrNull()
                assertTrue(body, e is AiException)
            }
    }

    @Test fun detectsMaxCompletionTokensError() {
        assertTrue(ChatCompletion.wantsMaxCompletionTokens(400, "Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead."))
        assertFalse(ChatCompletion.wantsMaxCompletionTokens(401, "max_tokens max_completion_tokens"))
    }
}
