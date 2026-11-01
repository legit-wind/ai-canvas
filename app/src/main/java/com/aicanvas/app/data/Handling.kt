package com.aicanvas.app.data

/** Rules for the things that go wrong: long input, big photos, busy servers, missed reminders. Unit-tested. */
object Limits {
    /** Longest message sent to the AI; longer pastes are cut so the request stays fast and affordable. */
    const val MAX_MESSAGE = 4_000
    /** Photos above this many bytes (after shrinking) are squeezed again at lower quality. */
    const val MAX_PHOTO_BYTES = 1_200_000
    const val PHOTO_EDGE = 1280
    /** Photos that can go with one message, and the most all of them together may weigh. */
    const val MAX_PHOTOS = 5
    const val MAX_TOTAL_PHOTO_BYTES = 4_000_000
    /** How big each photo may be when [count] go together, so the whole message stays under the total. */
    fun photoBudget(count: Int): Int = minOf(MAX_PHOTO_BYTES, MAX_TOTAL_PHOTO_BYTES / maxOf(1, count))
    /** How many more photos fit next to [have]. */
    fun roomFor(have: Int, picked: Int): Int = maxOf(0, minOf(picked, MAX_PHOTOS - have))

    /** The text to send and whether it was cut. Cuts at a line or word break near the limit when it can. */
    fun clip(text: String, max: Int = MAX_MESSAGE): Pair<String, Boolean> {
        if (text.length <= max) return text to false
        val head = text.take(max)
        val cut = maxOf(head.lastIndexOf('\n'), head.lastIndexOf(' ')).takeIf { it > max * 0.8 } ?: max
        return head.take(cut).trimEnd() to true
    }

    /** Power-of-two decode step so a photo of [w]×[h] decodes near [edge] px without using lots of memory. */
    fun sampleSize(w: Int, h: Int, edge: Int = PHOTO_EDGE): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= edge) s *= 2
        return s
    }

    /** JPEG qualities to try, best first, until the photo fits [MAX_PHOTO_BYTES]. */
    val PHOTO_QUALITIES = listOf(82, 70, 55, 40)
}

object Retry {
    const val MAX_ATTEMPTS = 3
    /** Busy (429), timeout (408) and server errors (5xx, incl. Anthropic's 529 "overloaded") are worth retrying. */
    fun worthRetry(code: Int): Boolean = code == 429 || code == 408 || code in 500..599

    /**
     * How long to wait before retry number [attempt] (1, 2, …): the server's Retry-After seconds when it gives
     * one, otherwise 2 s, 4 s, 8 s. Never more than 20 s, so the app doesn't look frozen.
     */
    fun delayMs(attempt: Int, retryAfter: String?): Long {
        val server = retryAfter?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong() }
        return (server ?: (2_000L shl (attempt - 1).coerceIn(0, 4))).coerceIn(500, 20_000)
    }
}

object Errors {
    /** A sentence the user can act on, for an HTTP error from an AI provider. */
    fun http(code: Int, message: String?): String {
        val m = message.orEmpty()
        return when {
            code == 401 -> "Your API key was rejected. Check it in Settings → AI model & key."
            code == 402 -> "Your account is out of credits for this model. Add credits or pick a free model."
            code == 403 -> "This API key was refused (403): it may lack access to that model or the API isn't enabled."
            code == 404 -> "Not found (404): the Base URL or model name is wrong. Check both in Settings → AI model & key${m.takeIf { it.isNotBlank() }?.let { " (" + it.take(120) + ")" } ?: ""}."
            code == 413 || (code == 400 && Regex("(?i)context|too long|too large|maximum.*tokens").containsMatchIn(m)) ->
                "That was too long for the model. Try a shorter message or a smaller photo."
            code == 400 && Regex("(?i)image|vision|multimodal").containsMatchIn(m) ->
                "This model can't read photos. Pick a vision model in Settings, or send text."
            code == 429 -> "The AI is busy (rate limit). I tried again a few times; wait a minute and retry."
            code == 529 || code == 503 -> "The AI service is overloaded right now. Try again in a minute."
            code >= 500 -> "The AI service had a problem ($code). Try again in a minute."
            else -> "API error $code: ${m.ifBlank { "no details" }.take(160)}"
        }
    }
}

object Readiness {
    /** Why a message can't be sent right now, or null when it can. */
    fun problem(hasKey: Boolean, online: Boolean): String? = when {
        !hasKey -> "Add your AI key first: Settings → AI model & key. Free models from OpenRouter work too."
        !online -> "You're offline. Connect to the internet and try again; your message is still in the box."
        else -> null
    }
}

object ReminderPlan {
    /** One-off reminders missed by less than this (phone off, battery saver) still show, marked as missed. */
    const val MISSED_GRACE = 12L * 60 * 60 * 1000

    /**
     * After a reboot, time change or app update: which reminders to book again (with their next time) and which
     * one-off reminders were missed while the phone was off. Older missed ones are dropped.
     */
    fun afterRestart(all: List<Reminder>, now: Long, next: (Reminder) -> Long): Pair<List<Reminder>, List<Reminder>> {
        val keep = mutableListOf<Reminder>()
        val missed = mutableListOf<Reminder>()
        all.forEach { r ->
            when {
                r.repeat.isNotEmpty() -> keep += r.copy(at = next(r))
                r.at > now -> keep += r
                now - r.at <= MISSED_GRACE -> missed += r
            }
        }
        return keep to missed
    }

    /** What to tell the user when the AI asked for reminders in the past (they are not booked). */
    fun pastNote(skipped: Int): String? = when (skipped) {
        0 -> null
        1 -> "That reminder time has already passed, so I didn't set it. Tell me a new time."
        else -> "$skipped reminder times had already passed, so I didn't set them."
    }
}

/** Keeps a reminder at the same wall-clock time ("9:00 AM") when the phone moves to another time zone. */
fun shiftForZone(at: Long, from: java.time.ZoneId, to: java.time.ZoneId): Long =
    java.time.Instant.ofEpochMilli(at).atZone(from).toLocalDateTime().atZone(to).toInstant().toEpochMilli()
