package com.focuslens

import android.content.Context

/** A short report for the developer: version, device, blocking status and the latest FocusLens log lines. */
fun buildDebugReport(context: Context): String {
    val log = try {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "300", "-s", "FocusLens:V"))
        process.inputStream.bufferedReader().readLines().takeLast(200).joinToString("\n")
    } catch (e: Exception) {
        "log unavailable: ${e.message}"
    }
    return buildString {
        appendLine("FocusLens ${BuildConfig.VERSION_NAME}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})")
        appendLine("Accessibility on: ${isAccessibilityOn(context)}")
        appendLine("Blocking service connected: ${FocusAccessibilityService.connected}")
        appendLine("Usage access: ${UsageRepository(context).hasPermission()}")
        appendLine("---- log ----")
        append(log)
    }
}
