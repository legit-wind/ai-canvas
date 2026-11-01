package com.aicanvas.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class FreeModel(val id: String, val name: String)

/** OpenRouter lists its models publicly; this finds the free text models so students can start at no cost. */
object OpenRouter {
    const val BASE_URL = "https://openrouter.ai/api/v1"
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    fun isOpenRouter(baseUrl: String) = baseUrl.contains("openrouter.ai")

    /** Keeps models whose prompt and completion prices are both zero and that answer in text. */
    fun freeModels(body: String): List<FreeModel> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val m = data.optJSONObject(i) ?: return@mapNotNull null
            val price = m.optJSONObject("pricing") ?: return@mapNotNull null
            if (price.optString("prompt").toDoubleOrNull() != 0.0 || price.optString("completion").toDoubleOrNull() != 0.0) return@mapNotNull null
            val out = m.optJSONObject("architecture")?.optJSONArray("output_modalities")
            if (out != null && (0 until out.length()).none { out.optString(it) == "text" }) return@mapNotNull null
            val id = m.optString("id").ifBlank { return@mapNotNull null }
            FreeModel(id, m.optString("name").ifBlank { id })
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    suspend fun discover(): List<FreeModel> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("$BASE_URL/models").build()).execute().use { r ->
            if (!r.isSuccessful) throw AiException("Couldn't load the model list (${r.code}). Try again.")
            freeModels(r.body?.string().orEmpty())
        }
    }
}
