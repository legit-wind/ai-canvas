package com.aicanvas.app.ai

import java.net.URI

/**
 * Which AI server addresses the app will talk to. HTTPS is always fine. Plain http is only allowed
 * to servers on the phone itself or on the local network (e.g. Ollama on a laptop over Wi-Fi), because
 * the API key and the user's notes would otherwise cross the internet unencrypted.
 */
object BaseUrls {
    /** Null when [url] is fine to use, otherwise a sentence explaining why not. */
    fun problem(url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return null // the default OpenAI address is used
        val uri = runCatching { URI(u) }.getOrNull()
        val host = uri?.host?.lowercase()
        if (uri == null || host.isNullOrEmpty()) return "That Base URL doesn't look like a web address."
        return when (uri.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (isLocal(host)) null else "Plain http is only allowed for servers on your own network. Use https."
            else -> "The Base URL must start with https:// (or http:// for a server on your own network)."
        }
    }

    /** Loopback, emulator host, private IPv4 ranges, link-local, IPv6 local, and *.local / *.lan names. */
    fun isLocal(host: String): Boolean {
        val h = host.trim('[', ']').lowercase()
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        if (h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:")) return h.contains(':')
        val p = h.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 127 || p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 169 && p[1] == 254) ||
            (p[0] == 100 && p[1] in 64..127) // CGNAT / Tailscale
    }
}

/** Google Gemini's OpenAI-compatible endpoint lives under /v1beta/openai/. */
object Gemini {
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"
    fun isGemini(url: String) = url.contains("generativelanguage.googleapis.com", ignoreCase = true)

    /** A Gemini base URL missing the /openai/ part is pointed at the right endpoint; others are unchanged. */
    fun normalize(url: String): String {
        val u = url.trim()
        if (!isGemini(u) || Regex("(?i)/openai/?$").containsMatchIn(u)) return u
        return BASE_URL
    }

    /** A note for the Settings screen when [url] would be corrected, else null. */
    fun hint(url: String): String? =
        if (isGemini(url) && normalize(url) != url.trim()) "Gemini needs $BASE_URL (used automatically)." else null
}

/** The human message inside an API error body: {"error":{"message"}} or Gemini's [{"error":{...}}]. */
fun apiErrorMessage(body: String): String? {
    val t = body.trim()
    val o = runCatching {
        if (t.startsWith("[")) org.json.JSONArray(t).optJSONObject(0) else org.json.JSONObject(t)
    }.getOrNull() ?: return null
    val e = o.opt("error")
    return when (e) {
        is org.json.JSONObject -> e.optString("message").ifBlank { e.optString("status") }
        is String -> e
        else -> o.optString("message")
    }.ifBlank { null }
}
