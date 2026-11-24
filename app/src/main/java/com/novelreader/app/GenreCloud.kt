package com.novelreader.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import java.util.Locale

/** Compact wrapping chips for selecting or displaying a book's genres. */
class GenreCloud(
    context: Context,
    private val ui: NativeUiComponents,
    private val accent: Int,
    private val accentText: Int,
    private val surfaceAlt: Int,
    private val textColor: Int
) : ViewGroup(context) {
    private var readOnly = false

    init {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun setReadOnly(value: Boolean) {
        readOnly = value
    }

    fun showGenres(genres: JSONArray?, selected: MutableSet<String>, query: String) {
        removeAllViews()
        if (genres == null) {
            requestLayout()
            return
        }
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        for (i in 0 until genres.length()) {
            val genre = genres.optString(i)
            if (!genre.lowercase(Locale.ROOT).contains(normalizedQuery)) continue
            val isSelected = selected.contains(genre)
            val chip = ui.text(genre, 13, if (isSelected) accentText else textColor).apply {
                gravity = android.view.Gravity.CENTER
                setPadding(ui.dp(14f), 0, ui.dp(14f), 0)
                background = ui.roundDrawable(
                    if (isSelected) accent else surfaceAlt,
                    if (isSelected) accent else ui.themedStrokeColor(0xFF40384A.toInt()),
                    18
                )
                isClickable = !readOnly
                isFocusable = !readOnly
            }
            if (!readOnly) {
                chip.setOnClickListener {
                    if (!selected.add(genre)) selected.remove(genre)
                    showGenres(genres, selected, query)
                }
                ui.applyPressScale(chip, .98f)
            }
            addView(chip, MarginLayoutParams(-2, ui.dp(38f)))
        }
        requestLayout()
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = MarginLayoutParams(-2, ui.dp(38f))

    override fun generateLayoutParams(params: ViewGroup.LayoutParams): ViewGroup.LayoutParams = MarginLayoutParams(params)

    override fun checkLayoutParams(params: ViewGroup.LayoutParams): Boolean = params is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        var x = 0
        var y = paddingTop
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            if (x + childWidth > maxWidth && x > 0) {
                x = 0
                y += rowHeight + ui.dp(6f)
                rowHeight = 0
            }
            x += childWidth + ui.dp(6f)
            rowHeight = maxOf(rowHeight, childHeight)
        }
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(y + rowHeight + paddingBottom + ui.dp(2f), heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        var x = width - paddingRight
        var y = paddingTop
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            val totalWidth = childWidth + params.leftMargin + params.rightMargin
            if (x - totalWidth < paddingLeft && x < width - paddingRight) {
                x = width - paddingRight
                y += rowHeight + ui.dp(6f)
                rowHeight = 0
            }
            val childRight = x - params.rightMargin
            val childLeft = childRight - childWidth
            child.layout(childLeft, y + params.topMargin, childRight, y + params.topMargin + childHeight)
            x -= totalWidth + ui.dp(6f)
            rowHeight = maxOf(rowHeight, childHeight + params.topMargin + params.bottomMargin)
        }
    }
}
