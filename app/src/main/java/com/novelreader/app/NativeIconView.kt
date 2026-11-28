package com.novelreader.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Renders icons from the centralized Android vector assets in the root icons directory. */
class NativeIconView(context: Context, name: String, tint: Int) : View(context) {
    private var name = name
    private var tint = tint
    private var icon: Drawable? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        loadIcon()
    }

    fun setTint(color: Int) {
        tint = color
        loadIcon()
    }

    fun setName(value: String) {
        name = value
        loadIcon()
    }

    private fun loadIcon() {
        var rotation = 0f
        val resourceName = when (name) {
            "menu" -> "menu"
            "add" -> "add"
            "delete" -> "delete"
            "image" -> "image"
            "battery" -> "battery_status"
            "dropdown" -> "chevron_down"
            "settings" -> "settings"
            "hide" -> "visibility_off"
            "show" -> "visibility"
            "select" -> "read_empty"
            "reorder" -> "drag_handle"
            "filter" -> "filter"
            "back" -> "direction".also { rotation = 180f }
            "next" -> "direction"
            "edit" -> "pencil"
            "book" -> "open_book"
            "help" -> "help"
            "undo" -> "undo"
            "redo" -> "redo"
            "check" -> "check"
            "thumb_up" -> "thumb_up"
            "thumb_down" -> "thumb_down"
            "close" -> "close"
            "scrolltop" -> "direction".also { rotation = 270f }
            "scrollbottom" -> "direction".also { rotation = 90f }
            "folder" -> "folder"
            "person" -> "person"
            "author_woman" -> "author_woman"
            "author_man" -> "author_man"
            "author_unknown" -> "author_unknown"
            "tag" -> "tag"
            "scan_text" -> "scan_text"
            "favorite", "heart_outline" -> "heart_outline"
            "heart_filled" -> "heart_filled"
            else -> name
        }

        val resourceId = resources.getIdentifier(resourceName, "drawable", context.packageName)
        icon = if (resourceId == 0) null else context.getDrawable(resourceId)?.mutate()?.also {
            it.setTint(tint)
        }
        this.rotation = rotation
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = icon ?: return

        val maxSize = min(width, height)
        val intrinsicWidth = max(1, drawable.intrinsicWidth)
        val intrinsicHeight = max(1, drawable.intrinsicHeight)
        val scale = min(maxSize / intrinsicWidth.toFloat(), maxSize / intrinsicHeight.toFloat())
        val iconWidth = (intrinsicWidth * scale).roundToInt()
        val iconHeight = (intrinsicHeight * scale).roundToInt()
        val left = (width - iconWidth) / 2
        val top = (height - iconHeight) / 2
        drawable.setBounds(left, top, left + iconWidth, top + iconHeight)
        drawable.draw(canvas)
    }
}
