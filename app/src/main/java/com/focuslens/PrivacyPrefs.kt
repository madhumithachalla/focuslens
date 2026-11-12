package com.focuslens

import android.content.Context
import com.focuslens.data.AppDatabase
import com.focuslens.data.UsageDatabase
import java.io.File

/**
 * Your choices about your data on this phone. Everything stays on the device; these settings only
 * control whether FocusLens learns from it and how you delete it.
 */
object PrivacyPrefs {
    private const val FILE = "privacy_prefs"
    private const val KEY_PERSONAL = "personalised"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** True once you've answered the consent question, yes or no. */
    fun answered(c: Context): Boolean = sp(c).contains(KEY_PERSONAL)

    /** Whether FocusLens learns from your history to make predictions. Off until you agree. */
    fun personalised(c: Context): Boolean = sp(c).getBoolean(KEY_PERSONAL, false)

    fun setPersonalised(c: Context, on: Boolean) {
        sp(c).edit().putBoolean(KEY_PERSONAL, on).apply()
    }

    /**
     * Deletes everything FocusLens has stored on this phone: the local database (tasks, check-ins,
     * journal, cycle, sleep, usage history), all settings and this consent choice. Does not touch
     * your Google account or any data already uploaded; use "Delete my synced usage" for that.
     * Call off the main thread.
     */
    fun deleteAllLocalData(c: Context) {
        AppDatabase.get(c).clearAllTables()
        UsageDatabase.get(c).clearAllTables()
        File(c.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            c.deleteSharedPreferences(f.name.removeSuffix(".xml"))
        }
        c.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        c.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** The wording shown with the consent question. Plain, and says what else the data is used for. */
    const val CONSENT_TEXT =
        "FocusLens learns from your tasks, check-ins, journal moods and screen time, on this phone, " +
        "to make predictions and insights for you.\n\n" +
        "Your data is also used to tune those predictions and to improve FocusLens features. " +
        "It is not sold and is not uploaded for these purposes.\n\n" +
        "You can opt out at any time. Predictions will be less accurate without your history. " +
        "You can also delete all of it on this phone in Settings."
}
