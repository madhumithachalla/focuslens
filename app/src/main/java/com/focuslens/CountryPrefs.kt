package com.focuslens

import android.content.Context
import java.util.Locale
import java.util.TimeZone

/**
 * The person's country, used to pick the right helplines. Detected from the phone's region
 * setting first, then from the time zone. If neither gives an answer, the person is asked once.
 * Stored on the phone in preferences only.
 */
object CountryPrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("country", Context.MODE_PRIVATE)

    /** Time zone prefixes that clearly point to one country. Only used when the region setting is missing. */
    private val ZONE_COUNTRY = mapOf(
        "Australia/" to "AU",
        "Pacific/Auckland" to "NZ",
        "Europe/London" to "GB",
        "America/New_York" to "US",
        "America/Los_Angeles" to "US",
        "America/Chicago" to "US",
        "America/Toronto" to "CA",
        "Asia/Kolkata" to "IN"
    )

    /** The country the person confirmed or chose, if any. */
    fun saved(c: Context): String? = sp(c).getString("code", null)

    fun save(c: Context, code: String) = sp(c).edit().putString("code", code.uppercase()).apply()

    /** Best guess from the phone: region setting, then time zone. Null if the phone gives no clue. */
    fun detect(): String? {
        val region = Locale.getDefault().country
        if (region.length == 2) return region.uppercase()
        val zone = TimeZone.getDefault().id
        ZONE_COUNTRY.entries.firstOrNull { (prefix, _) -> zone.startsWith(prefix) || zone == prefix }
            ?.let { return it.value }
        return null
    }

    /** The country to use: what the person saved, else the detected guess. Null means ask. */
    fun current(c: Context): String? = saved(c) ?: detect()
}
