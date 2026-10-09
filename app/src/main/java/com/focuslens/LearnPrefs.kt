package com.focuslens

import android.content.Context

/** Which Learn items have had their reflection confirmed, kept locally only. */
class LearnPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("learn", Context.MODE_PRIVATE)

    fun isRead(id: String): Boolean = sp.getBoolean("read_$id", false)

    fun markRead(id: String) {
        sp.edit().putBoolean("read_$id", true).apply()
    }

    fun totalRead(ids: List<String>): Int = ids.count { isRead(it) }
}
