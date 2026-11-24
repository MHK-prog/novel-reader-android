package com.novelreader.app

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Owns the short-lived continue-reading banner shown once after opening the library. */
class ResumeReadingPrompt(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var banner: View? = null
    private var dismissTask: Runnable? = null

    fun show(
        root: FrameLayout?,
        anchor: View?,
        title: String?,
        chapter: String?,
        typeface: Typeface?,
        surface: Int,
        text: Int,
        muted: Int,
        accent: Int,
        accentText: Int,
        onContinue: Runnable,
    ) {
        if (root == null || anchor == null || title == null || chapter == null) return
        dismiss()
        val density = activity.resources.displayMetrics.density

        val container = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(density, 14), dp(density, 10), dp(density, 14), dp(density, 10))
            background = rounded(surface, 0x665D4775, dp(density, 16))
            elevation = dp(density, 10).toFloat()
        }

        val close = FrameLayout(activity).apply {
            contentDescription = "بستن پیام ادامهٔ مطالعه"
            isClickable = true
            isFocusable = true
        }
        close.addView(
            NativeIconView(activity, "close", muted),
            FrameLayout.LayoutParams(dp(density, 20), dp(density, 20), Gravity.CENTER),
        )
        close.setOnClickListener { dismiss() }
        val closeParams = LinearLayout.LayoutParams(dp(density, 38), dp(density, 38)).apply {
            rightMargin = dp(density, 8)
        }
        container.addView(close, closeParams)

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.addView(label(title, typeface, text, 14), LinearLayout.LayoutParams(-1, -2))
        val chapterParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(density, 3) }
        labels.addView(label(chapter, typeface, muted, 12), chapterParams)
        container.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))

        val continueButton = label("ادامه", typeface, accentText, 14).apply {
            gravity = Gravity.CENTER
            setPadding(dp(density, 15), dp(density, 9), dp(density, 15), dp(density, 9))
            background = rounded(accent, accent, dp(density, 14))
            setOnClickListener { onContinue.run() }
        }
        val continueParams = LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(density, 12)
        }
        container.addView(continueButton, continueParams)

        val insertAt = root.indexOfChild(anchor).coerceAtLeast(0)
        val bannerParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            leftMargin = dp(density, 14)
            rightMargin = dp(density, 14)
            bottomMargin = dp(density, 12)
        }
        root.addView(container, insertAt, bannerParams)
        banner = container
        container.alpha = 0f
        container.translationY = dp(density, 14).toFloat()
        container.post { container.animate().alpha(1f).translationY(0f).setDuration(180).start() }

        dismissTask = Runnable { dismiss() }
        handler.postDelayed(dismissTask!!, 7_000L)
    }

    fun dismiss() {
        dismissTask?.let(handler::removeCallbacks)
        dismissTask = null
        val current = banner ?: return
        banner = null
        current.animate().cancel()
        current.animate()
            .alpha(0f)
            .translationY(dp(activity.resources.displayMetrics.density, 12).toFloat())
            .setDuration(140)
            .withEndAction {
                (current.parent as? ViewGroup)?.removeView(current)
            }
            .start()
    }

    fun dispose() = dismiss()

    private fun label(value: String, typeface: Typeface?, color: Int, size: Int): TextView =
        TextView(activity).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            setTypeface(typeface ?: Typeface.DEFAULT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

    private fun rounded(color: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke != 0) setStroke(dp(activity.resources.displayMetrics.density, 1), stroke)
        }

    private fun dp(density: Float, value: Int): Int = (density * value).roundToInt()
}
