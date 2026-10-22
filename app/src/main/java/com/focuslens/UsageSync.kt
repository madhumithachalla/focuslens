package com.focuslens

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/**
 * Opt-in, off by default: uploads the last 7 days of per-day screen-time totals (plus app
 * names and an hourly breakdown) to users/{uid}/data/usage_YYYYMMDD in the signed-in user's
 * own Firestore space. Every failure is logged and swallowed — sync must never crash the app.
 */
object UsageSync {

    private const val TAG = "UsageSync"
    private const val PREFS = "usage_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MIN_INTERVAL_MS = 30L * 60 * 1000
    private const val DAYS = 7
    private const val TOP_APPS = 15

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun isSignedIn(): Boolean = try {
        FirebaseAuth.getInstance().currentUser != null
    } catch (e: Exception) {
        false
    }

    /** Syncs only if opted in, signed in, and the last sync was 30+ minutes ago (or [force]). */
    suspend fun maybeSync(
        context: Context,
        repo: UsageRepository,
        history: UsageHistoryRepository,
        force: Boolean = false
    ) {
        try {
            if (!isEnabled(context) || !isSignedIn()) return
            val now = System.currentTimeMillis()
            if (!force && now - prefs(context).getLong(KEY_LAST_SYNC, 0L) < MIN_INTERVAL_MS) return
            if (!repo.hasPermission()) return
            // Stamp first so concurrent callers (and failures) don't retry in a tight loop.
            prefs(context).edit().putLong(KEY_LAST_SYNC, now).apply()
            withContext(Dispatchers.IO) { syncNow(repo, history) }
        } catch (e: Exception) {
            Log.w(TAG, "usage sync failed", e)
        }
    }

    private suspend fun syncNow(repo: UsageRepository, history: UsageHistoryRepository) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val todayStart = startOfDay(System.currentTimeMillis())

        for (back in 0 until DAYS) {
            try {
                val dayStart = todayStart - back * DAY_MS
                val dayEnd = minOf(dayStart + DAY_MS, System.currentTimeMillis())
                val date = fmt.format(Date(dayStart))

                val rows = history.tryExact(dayStart, dayStart + DAY_MS) ?: repo.loadCustom(dayStart, dayEnd)
                val totalMinutes = rows.sumOf { it.millis } / 60_000L
                val perApp = HashMap<String, Long>()
                rows.sortedByDescending { it.millis }.take(TOP_APPS).forEach { row ->
                    val key = row.label.replace(".", "·").replace("/", "-")
                    perApp[key] = (perApp[key] ?: 0L) + row.millis / 60_000L
                }
                val breakdown = repo.hourlyBreakdown(dayStart, dayEnd)
                val hourly = (0..23).map { h -> ((breakdown[h]?.values?.sum() ?: 0L) / 60_000L).toInt() }

                val doc = hashMapOf<String, Any>(
                    "date" to date,
                    "totalMinutes" to totalMinutes,
                    "perApp" to perApp,
                    "hourly" to hourly,
                    "updatedAt" to System.currentTimeMillis(),
                    "device" to Build.MODEL
                )
                db.collection("users").document(user.uid)
                    .collection("data").document("usage_$date")
                    .set(doc, SetOptions.merge()).awaitTask()
            } catch (e: Exception) {
                Log.w(TAG, "failed to sync a day", e)
            }
        }
    }

    /** Deletes every usage_* document for the signed-in user. Returns true on success. */
    suspend fun deleteAll(): Boolean = try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            false
        } else {
            val col = FirebaseFirestore.getInstance()
                .collection("users").document(user.uid).collection("data")
            val snap = col.get().awaitTask()
            for (d in snap.documents) {
                if (d.id.startsWith("usage_")) d.reference.delete().awaitTask()
            }
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "delete synced usage failed", e)
        false
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
