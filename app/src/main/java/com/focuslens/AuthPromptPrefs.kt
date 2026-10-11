package com.focuslens

import android.content.Context

/** Whether the first-run "sign in or skip" screen has already been shown once. */
class AuthPromptPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("auth_prompt", Context.MODE_PRIVATE)

    fun isSeen(): Boolean = sp.getBoolean("seen", false)
    fun setSeen() { sp.edit().putBoolean("seen", true).apply() }
}
