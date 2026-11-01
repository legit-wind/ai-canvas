package com.aicanvas.app.phone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Live data from free services that need no API key, so any AI model can show real weather, news and facts.
 * Each lookup is {"kind": "weather" | "news" | "wiki", "query": "..."}; the result is short text for the AI.
 */
object Lookup {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** A short line for the screen while lookups run, e.g. "Checking the weather in Pune…". */
    fun describe(lookups: List<JSONObject>): String {
        val first = lookups.firstOrNull() ?: return "Looking things up…"
        val query = first.optString("query").ifBlank { first.optString("place") }
        return when (first.optString("kind")) {
            "weather" -> if (query.isBlank()) "Checking the weather…" else "Checking the weather in $query…"
            "news" -> if (query.isBlank()) "Reading the news…" else "Reading news about $query…"
            "wiki" -> "Looking up $query…"
            "calc" -> "Working it out…"
            "calendar" -> "Reading your calendar…"
            "fx" -> "Checking exchange rates…"
            else -> "Looking things up…"
        }
    }

    suspend fun run(lookups: List<JSONObject>, calendar: ((JSONObject) -> String)? = null): String = coroutineScope {
        lookups.take(4).map { l ->
            async(Dispatchers.IO) {
                val kind = l.optString("kind")
                val query = l.optString("query").ifBlank { l.optString("place") }
                val result = runCatching {
                    when (kind) {
                        "weather" -> weather(query)
                        "news" -> news(query)
                        "wiki" -> wiki(query)
                        "calc" -> Calc.eval(query).let { r -> if (r == null) "Could not work that out." else Calc.format(r) }
                        "calendar" -> calendar?.invoke(l) ?: "Calendar access is off. The user can turn on Use my calendar in Settings → Calendar & permissions."
                        "fx" -> fx(query)
                        else -> "Unknown lookup kind."
                    }
                }.getOrElse { "Lookup failed (${it.message ?: "no connection"}). Tell the user this live data isn't available right now; don't make it up." }
                "[$kind: $query]\n$result"
            }
        }.awaitAll().joinToString("\n\n")
    }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", "AICanvas/1.0").build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

    private fun weather(place: String): String {
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", place).addQueryParameter("count", "1").build().toString()
        val hit = JSONObject(get(geoUrl)).optJSONArray("results")?.optJSONObject(0) ?: return "Place not found."
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", hit.optDouble("latitude").toString())
            .addQueryParameter("longitude", hit.optDouble("longitude").toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "5")
            .build().toString()
        val w = JSONObject(get(url))
        val c = w.optJSONObject("current") ?: JSONObject()
        val d = w.optJSONObject("daily") ?: JSONObject()
        val days = d.optJSONArray("time") ?: JSONArray()
        val forecast = (0 until days.length()).joinToString("\n") { i ->
            "${days.optString(i)}: ${describe(d.optJSONArray("weather_code")?.optInt(i) ?: -1)}, " +
                "${d.optJSONArray("temperature_2m_min")?.optDouble(i)}–${d.optJSONArray("temperature_2m_max")?.optDouble(i)}°C, " +
                "rain ${d.optJSONArray("precipitation_probability_max")?.optInt(i)}%"
        }
        return "${hit.optString("name")}, ${hit.optString("country")}. Now: ${describe(c.optInt("weather_code", -1))}, " +
            "${c.optDouble("temperature_2m")}°C (feels ${c.optDouble("apparent_temperature")}°C), " +
            "humidity ${c.optInt("relative_humidity_2m")}%, wind ${c.optDouble("wind_speed_10m")} km/h.\nNext days:\n$forecast"
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "partly cloudy"
        3 -> "cloudy"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        in 95..99 -> "thunderstorm"
        else -> "unknown"
    }

    private fun news(query: String): String {
        val country = Locale.getDefault().country.ifBlank { "US" }
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val base = if (query.isBlank()) "https://news.google.com/rss" else "https://news.google.com/rss/search"
        val url = base.toHttpUrl().newBuilder()
            .apply { if (query.isNotBlank()) addQueryParameter("q", query) }
            .addQueryParameter("hl", "$lang-$country").addQueryParameter("gl", country).addQueryParameter("ceid", "$country:$lang")
            .build().toString()
        val xml = get(url)
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).take(8).map { m ->
            val item = m.groupValues[1]
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1).orEmpty()
            val date = Regex("<pubDate>(.*?)</pubDate>").find(item)?.groupValues?.get(1).orEmpty()
            "- ${unescape(title)} ($date)"
        }.toList()
        return if (items.isEmpty()) "No news found." else items.joinToString("\n")
    }

    private fun wiki(query: String): String {
        val searchUrl = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "opensearch").addQueryParameter("search", query)
            .addQueryParameter("limit", "1").addQueryParameter("format", "json").build().toString()
        val title = JSONArray(get(searchUrl)).optJSONArray(1)?.optString(0)?.ifBlank { null } ?: return "Nothing found."
        val summary = JSONObject(get("https://en.wikipedia.org/api/rest_v1/page/summary/" + title.replace(' ', '_')))
        return "$title: ${summary.optString("extract")}"
    }

    /** Live exchange rate from open.er-api.com (free, no key), e.g. "100 USD INR". */
    private fun fx(query: String): String {
        val (amount, from, to) = Fx.parse(query) ?: return "Give the currencies as codes, e.g. \"100 USD INR\"."
        val o = JSONObject(get("https://open.er-api.com/v6/latest/$from"))
        if (o.optString("result") != "success") return "Exchange rates aren't available right now."
        val rate = o.optJSONObject("rates")?.optDouble(to, Double.NaN)?.takeIf { !it.isNaN() } ?: return "Unknown currency $to."
        return "1 $from = ${"%.4f".format(rate)} $to (updated ${o.optString("time_last_update_utc")}). " +
            "${Calc.format(amount)} $from = ${"%.2f".format(amount * rate)} $to."
    }

    private fun unescape(s: String) = s
        .replace("<![CDATA[", "").replace("]]>", "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
}

/** Reads "100 usd to inr", "USD INR", "₹500 in dollars" into (amount, FROM, TO). Pure, unit-tested. */
object Fx {
    private val NAMES = mapOf(
        "₹" to "INR", "rupee" to "INR", "rupees" to "INR", "rs" to "INR", "$" to "USD", "dollar" to "USD", "dollars" to "USD",
        "€" to "EUR", "euro" to "EUR", "euros" to "EUR", "£" to "GBP", "pound" to "GBP", "pounds" to "GBP", "yen" to "JPY", "dirham" to "AED",
    )

    fun parse(query: String): Triple<Double, String, String>? {
        val q = query.lowercase().replace(",", "")
        val amount = Regex("\\d+(\\.\\d+)?").find(q)?.value?.toDoubleOrNull() ?: 1.0
        val codes = Regex("[₹$€£]|[a-z]+").findAll(q).map { it.value }
            .mapNotNull { w -> NAMES[w] ?: w.uppercase().takeIf { w.length == 3 && w !in setOf("and", "the", "for", "how") } }
            .toList()
        if (codes.size < 2) return null
        return Triple(amount, codes[0], codes[1])
    }
}
