package com.aicanvas.app.ai

import org.json.JSONException
import org.json.JSONObject

/** Reading answers from OpenAI-compatible "/chat/completions" servers, which vary a lot. */
object ChatCompletion {
    /** The assistant's text from a successful response body, without <think> blocks. Throws [AiException]. */
    fun content(body: String): String {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw AiException("The AI server sent an answer the app can't read. Check the Base URL in the menu.")
        }
        root.optJSONObject("error")?.let { err ->
            throw AiException("The AI server reported an error: ${err.optString("message").ifBlank { "unknown" }.take(160)}")
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: throw AiException("The AI server sent no answer. Try again or pick another model.")
        val message = choice.optJSONObject("message")
        val text = if (message == null || message.isNull("content")) "" else message.optString("content")
        val cleaned = text.replace(Regex("(?s)<think>.*?</think>"), "").replace(Regex("(?s)<think>.*"), "").trim()
        // Hit the token cap: hand back whatever arrived so the caller can retry bigger or keep the finished part.
        if (choice.optString("finish_reason") == "length") throw Truncated(cleaned)
        if (cleaned.isBlank()) throw AiException("The AI sent an empty answer. Try again or pick another model.")
        return cleaned
    }

    /** True when a 400 error says the server wants "max_completion_tokens" instead of "max_tokens". */
    fun wantsMaxCompletionTokens(code: Int, body: String): Boolean =
        code == 400 && body.contains("max_tokens") && body.contains("max_completion_tokens")
}

/** The answer stopped at the token cap; [partial] is what arrived (maybe empty). */
class Truncated(val partial: String) : Exception("The answer was cut off")

/** Rescues a JSON answer that was cut off mid-way: keeps every value that was complete and closes the rest. */
object Salvage {
    /** The longest complete-looking prefix of [raw] closed into one valid JSON object, or null. */
    fun close(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        val stack = ArrayDeque<Char>()
        val cuts = mutableListOf<Pair<Int, String>>() // position after a finished value -> closers needed there
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> stack.addLast('}')
                '[' -> stack.addLast(']')
                '}', ']' -> {
                    if (stack.isEmpty() || stack.removeLast() != c) return null
                    if (stack.isEmpty()) return runCatching { JSONObject(raw.substring(start, i + 1)) }.getOrNull()
                    cuts += (i + 1) to stack.reversed().joinToString("")
                }
            }
        }
        for ((at, closers) in cuts.asReversed()) {
            runCatching { JSONObject(raw.substring(start, at) + closers) }.getOrNull()?.let { return it }
        }
        return null
    }
}
