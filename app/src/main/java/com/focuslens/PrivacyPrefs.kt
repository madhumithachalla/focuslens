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
        UsageTracking.apply(c, false)
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
        "Yes: FocusLens saves your app usage on this phone every 6 hours, so your numbers are more accurate.\n\n" +
        "No: FocusLens shows Digital Wellbeing's numbers instead.\n\n" +
        "We don't sell your data, and we don't use it to train AI models outside FocusLens. " +
        "You can change this in Settings any time."
}
