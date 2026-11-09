package com.focuslens

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button

/**
 * A small "Skip reel" button drawn over Instagram while you are in the feed, stories or Reels.
 * Tapping it asks the accessibility service to move to the next item, the same as a swipe.
 * It only appears when the person has turned it on and given "Display over other apps".
 */
object SkipReelOverlay {
    private var view: View? = null

    fun show(context: Context, onSkip: () -> Unit) {
        if (view != null) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val button = Button(context).apply {
            text = "Skip reel"
            textSize = 14f
            setOnClickListener { onSkip() }
        }
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
            wm.addView(button, params)
            view = button
        } catch (e: Exception) {
            // No permission, or the window could not be added; the button simply does not show.
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
