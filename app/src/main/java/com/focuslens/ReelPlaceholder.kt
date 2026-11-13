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
    private var fullText = ""
    private var counterView: View? = null
    const val DEFAULT_FULL_TEXT = "Reels is restricted in FocusLens."

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
                textSize = if (padding >= 48) 18f else if (padding > 16) 14f else 12f
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

    /**
     * A small card at the bottom for a Reels tab or player. It is deliberately NOT full screen: a
     * full-screen overlay covered the navigation buttons and stopped the person leaving the app.
     */
    fun showFull(c: Context, text: String = DEFAULT_FULL_TEXT, onBack: () -> Unit) {
        if (fullView != null && fullText == text) return
        clearFull(c)
        val v = card(c, text, 24, onBack)
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 160
        }
        try {
            windows(c).addView(v, lp)
            fullView = v
            fullText = text
        } catch (e: Exception) {
            // Could not be shown; the Reels screen stays visible.
        }
    }

    /** A small chip at the top right, e.g. "Reels: 12 used · 8 left today". */
    fun showCounter(c: Context, text: String) {
        val existing = counterView
        if (existing != null) {
            (existing as? TextView)?.text = text
            return
        }
        val chip = TextView(c).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF2E2A25.toInt())
            setBackgroundColor(0xF2F6F0E6.toInt())
            setPadding(20, 8, 20, 8)
        }
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 120
        }
        try {
            windows(c).addView(chip, lp)
            counterView = chip
        } catch (e: Exception) {
            // Not shown; the limit still applies.
        }
    }

    private var bannerView: View? = null

    /** A short banner at the bottom, not a full page. Used when the reel limit is reached. */
    fun showBanner(c: Context, text: String, onBack: () -> Unit) {
        if (bannerView != null) return
        val v = card(c, text, 24, onBack)
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 160
        }
        try {
            windows(c).addView(v, lp)
            bannerView = v
        } catch (e: Exception) {
            // Not shown.
        }
    }

    fun clearBanner(c: Context) {
        val v = bannerView ?: return
        try { windows(c).removeView(v) } catch (e: Exception) { }
        bannerView = null
    }

    fun clearCounter(c: Context) {
        val v = counterView ?: return
        try { windows(c).removeView(v) } catch (e: Exception) { }
        counterView = null
    }

    fun clearRects(c: Context) {
        rectViews.forEach { v -> try { windows(c).removeView(v) } catch (e: Exception) { } }
        rectViews.clear()
    }

    fun clearFull(c: Context) {
        val v = fullView ?: return
        try { windows(c).removeView(v) } catch (e: Exception) { }
        fullView = null
        fullText = ""
    }

    fun hideAll(c: Context) {
        clearRects(c)
        clearFull(c)
        clearCounter(c)
        clearBanner(c)
    }
}
