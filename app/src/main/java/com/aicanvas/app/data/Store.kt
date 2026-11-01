package com.aicanvas.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val PROVIDER_ANTHROPIC = "anthropic"
const val PROVIDER_OPENAI = "openai"

/** The default Claude model: the low-cost one, which is plenty for designing small cards. */
const val DEFAULT_MODEL = "claude-haiku-5-5"

data class AppSettings(
    val provider: String = PROVIDER_ANTHROPIC,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** Only used by OpenAI-compatible providers. */
    val baseUrl: String = "",
) {
    val isConfigured get() = apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * One element on the home screen, designed by the AI.
 * [ui] is a tree of UI building blocks (see Ai.SYSTEM). [size] is "full" or "half"; [tone] tints the card.
 * The screen is sorted by [importance] (0-100, set by the AI). [placed] means the user dragged it there,
 * so the AI leaves its importance alone.
 */
data class Element(
    val id: String = newId(),
    val size: String = "full",
    val tone: String = "none",
    val ui: JSONObject,
    val importance: Double = 50.0,
    val placed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** The user tapped the folded card open (kept across restarts). */
    val expanded: Boolean = false,
    /** The canvas (screen) this card is on; see [Canvases]. */
    val canvas: String = Canvases.HOME_ID,
    /** For live cards: where fresh content comes from, e.g. {"kind":"weather","query":"Pune"}; see [Refresh]. */
    val refresh: JSONObject? = null,
    /** When the live content was last refreshed (epoch millis), 0 = never. */
    val refreshedAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("size", size).put("tone", tone).put("ui", ui)
        .put("importance", importance).put("placed", placed).put("createdAt", createdAt).put("expanded", expanded)
        .put("canvas", canvas).put("refreshedAt", refreshedAt).also { o -> refresh?.let { o.put("refresh", it) } }

    companion object {
        fun fromJson(o: JSONObject) = Element(
            id = o.getString("id"),
            size = o.optString("size", "full"),
            tone = o.optString("tone", "none"),
            ui = o.optJSONObject("ui") ?: JSONObject(),
            importance = o.optDouble("importance", 50.0),
            placed = o.optBoolean("placed"),
            createdAt = o.optLong("createdAt"),
            expanded = o.optBoolean("expanded"),
            canvas = o.optString("canvas").ifBlank { Canvases.HOME_ID },
            refresh = o.optJSONObject("refresh"),
            refreshedAt = o.optLong("refreshedAt"),
        )
    }
}

/** Most important first; among equals, the newest first. */
val byImportance: Comparator<Element> = compareByDescending<Element> { it.importance }.thenByDescending { it.createdAt }

/** Something the AI remembers about the user, e.g. "Lives in Hyderabad". */
data class Memory(val id: String = newId(), val text: String, val createdAt: Long = System.currentTimeMillis()) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Memory(o.getString("id"), o.optString("text"), o.optLong("createdAt"))
    }
}

/**
 * A phone notification at [at] (epoch millis). [repeat] is "", "daily" or "weekly".
 * [elementId] is the card it belongs to, so deleting the card cancels it ("" when it stands alone).
 */
data class Reminder(val id: String = newId(), val at: Long, val text: String, val repeat: String = "", val elementId: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("at", at).put("text", text).put("repeat", repeat).put("elementId", elementId)

    companion object {
        fun fromJson(o: JSONObject) =
            Reminder(o.getString("id"), o.optLong("at"), o.optString("text"), o.optString("repeat"), o.optString("elementId"))
    }
}

fun newId(): String = UUID.randomUUID().toString().take(8)

/** Settings live in SharedPreferences (API key encrypted); everything else in small JSON files. */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadSettings() = AppSettings(
        provider = prefs.getString("provider", PROVIDER_ANTHROPIC)!!,
        apiKey = prefs.getString("apiKey", null)?.let { KeyCrypto.decrypt(it) } ?: "",
        model = prefs.getString("model", DEFAULT_MODEL)!!,
        baseUrl = prefs.getString("baseUrl", "")!!,
    )

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("provider", s.provider)
            .putString("apiKey", KeyCrypto.encrypt(s.apiKey))
            .putString("model", s.model)
            .putString("baseUrl", s.baseUrl)
            .apply()
    }

    /** Appearance: "system", "light" or "dark". */
    var theme: String
        get() = prefs.getString("theme", "system")!!
        set(v) = prefs.edit().putString("theme", v).apply()

    /** Appearance: accent colour name ("default" or a palette colour). */
    var textSize: String
        get() = prefs.getString("text_size", "default")!!
        set(v) = prefs.edit().putString("text_size", v).apply()

    var accent: String
        get() = prefs.getString("accent", "default")!!
        set(v) = prefs.edit().putString("accent", v).apply()

    /** Whether the AI may read the phone calendar. */
    var useCalendar: Boolean
        get() = prefs.getBoolean("useCalendar", false)
        set(v) = prefs.edit().putBoolean("useCalendar", v).apply()

    /** When the morning briefing goes off ("HH:mm"), or "" when it is off. */
    var briefingTime: String
        get() = prefs.getString("briefingTime", "")!!
        set(v) = prefs.edit().putString("briefingTime", v).apply()

    /** The day (yyyy-MM-dd) the AI last tidied the screen. */
    /** The phone's time zone when reminders were last booked, so a zone change keeps them at the same clock time. */
    var lastZone: String
        get() = prefs.getString("lastZone", "")!!
        set(v) = prefs.edit().putString("lastZone", v).apply()

    /** The canvases, as JSON (see [Canvases.serialize]). */
    var canvasesJson: String
        get() = prefs.getString("canvases", "")!!
        set(v) = prefs.edit().putString("canvases", v).apply()

    var currentCanvas: String
        get() = prefs.getString("currentCanvas", Canvases.HOME_ID)!!
        set(v) = prefs.edit().putString("currentCanvas", v).apply()

    /** Put new AI cards on the canvas matching their category instead of the current one. */
    var autoPlace: Boolean
        get() = prefs.getBoolean("autoPlace", false)
        set(v) = prefs.edit().putBoolean("autoPlace", v).apply()

    /** Set by the background refresh when it rewrote elements.json, so an open app reloads them. */
    var backgroundRefreshAt: Long
        get() = prefs.getLong("bgRefreshAt", 0L)
        set(v) = prefs.edit().putLong("bgRefreshAt", v).apply()

    var lastTidy: String
        get() = prefs.getString("lastTidy", "")!!
        set(v) = prefs.edit().putString("lastTidy", v).apply()

    // Items that can't be read are skipped one by one instead of losing the whole list.
    fun loadElements() = readArray("elements.json").mapNotNull { runCatching { Element.fromJson(it) }.getOrNull() }
    /** Returns false when the file could not be written; the caller should tell the user. */
    fun saveElements(list: List<Element>) = writeArray("elements.json", list.map { it.toJson() })

    fun loadMemories() = readArray("memories.json").mapNotNull { runCatching { Memory.fromJson(it) }.getOrNull() }
    fun saveMemories(list: List<Memory>) = writeArray("memories.json", list.map { it.toJson() })

    fun loadReminders() = readArray("reminders.json").mapNotNull { runCatching { Reminder.fromJson(it) }.getOrNull() }
    fun loadHabits(): List<Habit> = readArray("habits.json").mapNotNull { Habit.fromJson(it) }
    fun saveHabits(list: List<Habit>) = writeArray("habits.json", list.map { it.toJson() })
    fun loadCountdowns(): List<Countdown> = readArray("countdowns.json").mapNotNull { Countdown.fromJson(it) }
    fun saveCountdowns(list: List<Countdown>) = writeArray("countdowns.json", list.map { it.toJson() })
    fun loadTasks(): List<TaskItem> = readArray("tasks.json").mapNotNull { TaskItem.fromJson(it) }
    fun saveTasks(list: List<TaskItem>) = writeArray("tasks.json", list.map { it.toJson() })
    fun loadExpenses(): List<Expense> = readArray("expenses.json").mapNotNull { Expense.fromJson(it) }
    fun saveExpenses(list: List<Expense>) = writeArray("expenses.json", list.map { it.toJson() })

    var expenseSettings: ExpenseSettings
        get() = ExpenseSettings.fromJson(prefs.getString("expenseSettings", ""))
        set(v) = prefs.edit().putString("expenseSettings", v.toJson().toString()).apply()

    /** Whether old AI expense-log cards were already read into the tracker. */
    var expensesMigrated: Boolean
        get() = prefs.getBoolean("expensesMigrated", false)
        set(v) = prefs.edit().putBoolean("expensesMigrated", v).apply()

    /** Auto-add a live home card when the first task / habit / countdown / expense is added. */
    var moduleCards: Boolean
        get() = prefs.getBoolean("moduleCards", true)
        set(v) = prefs.edit().putBoolean("moduleCards", v).apply()

    /** Module kinds whose home card the user removed (comma-separated); they aren't re-added automatically. */
    var hiddenModules: Set<String>
        get() = prefs.getString("hiddenModules", "")!!.split(',').filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("hiddenModules", v.joinToString(",")).apply()

    fun saveReminders(list: List<Reminder>) = writeArray("reminders.json", list.map { it.toJson() })

    /**
     * Reads a JSON array file. If the file exists but can't be parsed, it is moved aside to
     * "<name>.corrupt-<time>" (never overwritten or deleted), so the user's data can still be recovered.
     */
    @Synchronized
    private fun readArray(name: String): List<JSONObject> {
        val f = File(context.filesDir, name)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(AtomicFile(f).readFully().decodeToString())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (e: Exception) {
            val aside = File(context.filesDir, "$name.corrupt-${System.currentTimeMillis()}")
            val moved = f.renameTo(aside) || runCatching { f.copyTo(aside); true }.getOrDefault(false)
            Log.w(TAG, "Couldn't read $name; kept it as ${aside.name} (moved=$moved)", e)
            loadFailures += name
            // If the bad file couldn't be moved aside, refuse to write over it.
            if (!moved) protectedFiles += name
            emptyList()
        }
    }

    /** Atomically writes a JSON array file. Returns false (and keeps the old file) when the write fails. */
    @Synchronized
    private fun writeArray(name: String, items: List<JSONObject>): Boolean {
        if (name in protectedFiles) {
            Log.w(TAG, "Not saving $name: it failed to load and could not be backed up")
            return false
        }
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        val file = AtomicFile(File(context.filesDir, name))
        var out: java.io.FileOutputStream? = null
        return try {
            out = file.startWrite()
            out.write(arr.toString().toByteArray())
            file.finishWrite(out)
            true
        } catch (e: Exception) {
            out?.let { file.failWrite(it) }
            Log.w(TAG, "Couldn't save $name", e)
            false
        }
    }

    companion object {
        private const val TAG = "AICanvas"
        /** Files that were found unreadable this session (the original was kept as a .corrupt copy). */
        val loadFailures: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
        private val protectedFiles: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    }
}

/** Encrypts the API key with a hardware-backed key from the Android Keystore (AES-GCM). */
object KeyCrypto {
    private const val ALIAS = "ai_canvas_api_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(c.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        return "$iv:$data"
    }

    fun decrypt(stored: String): String? {
        if (stored.isEmpty()) return ""
        return try {
            val (iv, data) = stored.split(":", limit = 2)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        } catch (e: Exception) {
            null
        }
    }
}
