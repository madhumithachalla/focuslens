package com.focuslens

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A calm "Reels is restricted" card drawn over the app. Two forms:
 * - a full-screen card for a Reels tab or player, with a Back button (no kick-out to home);
 * - a small cover over one reel that sits inside the feed, so the rest of the feed still works.
 * Needs "Display over other apps".
 */
object ReelPlaceholder {
    private val rectViews = ArrayList<View>()
    private var fullView: View? = null

    private fun windows(c: Context) = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun card(c: Context, text: String, padding: Int, onBack: (() -> Unit)?): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF2F6F0E6.toInt())   // warm cream, almost solid
            setPadding(padding, padding, padding, padding)
            addView(TextView(c).apply {
                this.text = text
                textSize = if (padding > 16) 18f else 12f
                gravity = Gravity.CENTER
                setTextColor(0xFF2E2A25.toInt())
            })
            onBack?.let { back ->
                addView(Button(c).apply {
                    this.text = "Back to my feed"
                    setOnClickListener { back() }
                })
            }
        }

    /** Covers each reel rectangle (screen pixels) that sits inside the feed. Replaces earlier covers. */
    fun showOver(c: Context, rects: List<Rect>) {
        clearRects(c)
        rects.forEach { r ->
            if (r.width() <= 0 || r.height() <= 0) return@forEach
            val v = card(c, "Reel hidden. Reels is restricted in FocusLens.", 8, null)
            val lp = params(r.width(), r.height()).apply {
                gravity = Gravity.TOP or Gravity.START
                x = r.left
                y = r.top
            }
            try {
                windows(c).addView(v, lp)
                rectViews += v
            } catch (e: Exception) {
                // No permission, or the window could not be added: nothing is covered.
            }
        }
    }

    /** Full-screen restricted card for a Reels tab or player. Back leaves the Reels screen. */
    fun showFull(c: Context, onBack: () -> Unit) {
        if (fullView != null) return
        val v = card(c, "Reels is restricted in FocusLens.\nThe rest of the app still works.", 48, onBack)
        try {
            windows(c).addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
            fullView = v
        } catch (e: Exception) {
            // Could not be shown; the Reels screen stays visible.
        }
    }

    fun clearRects(c: Context) {
        rectViews.forEach { v -> try { windows(c).removeView(v) } catch (e: Exception) { } }
        rectViews.clear()
    }

    fun clearFull(c: Context) {
        val v = fullView ?: return
        try { windows(c).removeView(v) } catch (e: Exception) { }
        fullView = null
    }

    fun hideAll(c: Context) {
        clearRects(c)
        clearFull(c)
    }
}
