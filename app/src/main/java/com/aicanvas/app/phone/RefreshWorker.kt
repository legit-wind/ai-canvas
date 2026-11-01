package com.aicanvas.app.phone

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aicanvas.app.ai.Ai
import com.aicanvas.app.ai.Snapshot
import com.aicanvas.app.data.Refresh
import com.aicanvas.app.data.Store
import com.aicanvas.app.data.UiSchema
import java.util.concurrent.TimeUnit

/**
 * Once a day (on any network) refreshes the live cards in place: fresh lookups, then the AI rewrites only those
 * cards (same ids). With no key, it does nothing and the cards keep their old content and "Updated …" time.
 * Skipped while the app is open; the app refreshes stale cards itself then.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Foreground.visible) return Result.success()
        val store = Store(applicationContext)
        val settings = store.loadSettings()
        if (!settings.isConfigured || !Network.isOnline(applicationContext)) return Result.success()
        val elements = store.loadElements()
        val now = System.currentTimeMillis()
        fun spec(e: com.aicanvas.app.data.Element) = e.refresh ?: Refresh.guess(e.ui, com.aicanvas.app.data.CardSummary.fold(e.ui).title)
        val due = elements.filter { Refresh.isStale(spec(it), it.refreshedAt, now) }
        if (due.isEmpty()) return Result.success()
        return try {
            val lookups = due.mapNotNull { spec(it) }.filter { it.optString("kind") in setOf("weather", "news", "fx") }.distinctBy { it.toString() }
            val fresh = if (lookups.isEmpty()) "" else runCatching { Lookup.run(lookups) }.getOrNull()?.let { "\n<lookup_results>\n$it\n</lookup_results>" } ?: ""
            val ids = due.map { it.id }.toSet()
            val r = Ai.ask(settings, Snapshot(elements, store.loadMemories(), store.loadReminders()), emptyList(), Refresh.prompt(ids.toList()) + fresh)
            if (Foreground.visible) return Result.success() // the app opened meanwhile; let it refresh instead
            val t = System.currentTimeMillis()
            val updated = elements.map { e ->
                if (e.id !in ids) return@map e
                val u = r.update.firstOrNull { it.optString("id") == e.id }
                e.copy(ui = u?.optJSONObject("ui")?.let { UiSchema.keepUserState(e.ui, it) } ?: e.ui, refresh = spec(e), refreshedAt = t)
            }
            if (store.saveElements(updated)) store.backgroundRefreshAt = t
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("live-refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/** Whether the app is on screen, so background work doesn't write over what the open app holds. */
object Foreground { @Volatile var visible = false }
