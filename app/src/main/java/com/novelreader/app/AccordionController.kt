package com.novelreader.app

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

/** Keeps one expandable panel open at a time and animates its height. */
internal class AccordionController {
    private var expandedContent: View? = null
    private var expandedIndicator: View? = null
    private var animator: ValueAnimator? = null

    fun isExpanded(content: View?): Boolean = content != null && expandedContent === content

    fun toggle(content: View?, indicator: View?) {
        if (content == null) return
        if (expandedContent === content) {
            collapse(content, indicator)
            expandedContent = null
            expandedIndicator = null
            return
        }
        expandedContent?.let { closeImmediately(it, expandedIndicator) }
        expandedContent = content
        expandedIndicator = indicator
        indicator?.animate()?.rotation(180f)?.setDuration(180)?.setInterpolator(DecelerateInterpolator())?.start()
        expand(content)
    }

    fun reset() {
        animator?.cancel()
        animator = null
        expandedContent?.let {
            it.visibility = View.GONE
            it.alpha = 1f
            it.translationY = 0f
            it.layoutParams?.let { params ->
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                it.layoutParams = params
            }
        }
        expandedIndicator?.rotation = 0f
        expandedContent = null
        expandedIndicator = null
    }

    private fun closeImmediately(content: View, indicator: View?) {
        content.visibility = View.GONE
        content.alpha = 1f
        content.translationY = 0f
        content.layoutParams?.let { params ->
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            content.layoutParams = params
        }
        indicator?.animate()?.rotation(0f)?.setDuration(120)?.setInterpolator(DecelerateInterpolator())?.start()
    }

    private fun expand(content: View) {
        animator?.cancel()
        content.visibility = View.VISIBLE
        val parentWidth = (content.parent as? View)?.width
            ?.takeIf { it > 0 } ?: content.resources.displayMetrics.widthPixels
        content.measure(
            View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val target = content.measuredHeight
        val params = content.layoutParams ?: ViewGroup.LayoutParams(-1, 0)
        params.height = 0
        content.layoutParams = params
        animator = ValueAnimator.ofInt(0, target).apply {
            duration = 190
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val current = content.layoutParams ?: return@addUpdateListener
                current.height = it.animatedValue as Int
                content.layoutParams = current
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (expandedContent === content) {
                        val current = content.layoutParams ?: return
                        current.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        content.layoutParams = current
                    }
                }
            })
            start()
        }
    }

    private fun collapse(content: View, indicator: View?) {
        animator?.cancel()
        indicator?.animate()?.rotation(0f)?.setDuration(180)?.setInterpolator(DecelerateInterpolator())?.start()
        val start = content.height.takeIf { it > 0 } ?: content.measuredHeight
        if (content.visibility != View.VISIBLE || start <= 0) {
            content.visibility = View.GONE
            content.layoutParams?.let { params ->
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                content.layoutParams = params
            }
            return
        }
        animator = ValueAnimator.ofInt(start, 0).apply {
            duration = 160
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val current = content.layoutParams ?: return@addUpdateListener
                current.height = it.animatedValue as Int
                content.layoutParams = current
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (expandedContent !== content) {
                        content.visibility = View.GONE
                        content.alpha = 1f
                        content.translationY = 0f
                        val current = content.layoutParams ?: return
                        current.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        content.layoutParams = current
                    }
                }
            })
            start()
        }
    }
}
