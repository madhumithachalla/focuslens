package com.focuslens

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

/**
 * A small "Skip reel" button drawn over Instagram while you are in the feed, stories or Reels.
 * Skip asks the accessibility service to move on, the same as a swipe. Ignore hides the buttons
 * for ten minutes, so they never sit in the way when you have decided to watch. It only appears
 * when the person has turned it on and given "Display over other apps".
 */
object SkipReelOverlay {
    private var view: View? = null
    @Volatile private var ignoredUntil = 0L
    private const val IGNORE_MINUTES = 10L

    fun show(context: Context, onSkip: () -> Unit) {
        if (view != null) return
        if (System.currentTimeMillis() < ignoredUntil) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val skip = Button(context).apply {
            text = "Skip reel"
            textSize = 14f
            setOnClickListener { onSkip() }
        }
        val ignore = Button(context).apply {
            text = "Ignore"
            textSize = 12f
            setOnClickListener {
                ignoredUntil = System.currentTimeMillis() + IGNORE_MINUTES * 60_000L
                hide(context)
            }
        }
        row.addView(skip)
        row.addView(ignore)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 180
        }
        try {
            wm.addView(row, params)
            view = row
        } catch (e: Exception) {
            // No permission, or the window could not be added; the buttons simply do not show.
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
            // Already gone.
        }
        view = null
    }
}
