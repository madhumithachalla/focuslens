package com.focuslens

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Saves FocusLens's own usage snapshots. Does nothing unless the user said yes. */
class UsageSnapshotWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!PrivacyPrefs.personalised(applicationContext)) return Result.success()
        return try {
            val history = UsageHistoryRepository(applicationContext, UsageRepository(applicationContext))
            history.catchUp()
            history.catchUpHourly()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object UsageTracking {
    private const val WORK_NAME = "usage_snapshots"

    /** Turns the 6-hourly snapshot job on after a yes, or off after a no. */
    fun apply(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (on) {
            val request = PeriodicWorkRequestBuilder<UsageSnapshotWorker>(6, TimeUnit.HOURS).build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK_NAME)
        }
    }
}
