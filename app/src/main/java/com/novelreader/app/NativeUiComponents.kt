package com.novelreader.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Shared native control styling used by all screens. */
class NativeUiComponents(private val activity: Activity) {
    data class Theme(
        val surface: Int,
        val surfaceAlt: Int,
        val accent: Int,
        val secondaryAccent: Int,
        val text: Int,
        val muted: Int,
        val light: Boolean,
        val accentIndex: Int,
        val typeface: Typeface?,
        val catalog: ThemeCatalog
    )

    private lateinit var theme: Theme

    fun updateTheme(value: Theme) {
        theme = value
    }

    fun dp(value: Float): Int = Math.round(value * activity.resources.displayMetrics.density)

    fun text(value: String, sp: Int, color: Int): TextView = TextView(activity).apply {
        text = value
        textSize = sp.toFloat()
        setTextColor(color)
        typeface = theme.typeface ?: Typeface.DEFAULT
        gravity = Gravity.CENTER_VERTICAL
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
    }

    fun emptyLabel(value: String): TextView = text(value, 15, theme.muted).apply {
        gravity = Gravity.CENTER
    }

    fun edit(hint: String, multiline: Boolean): EditText {
        val view = if (multiline) ReaderEditText(activity) else EditText(activity)
        view.setTextColor(theme.text)
        view.setHintTextColor(0xFF77717D.toInt())
        view.typeface = theme.typeface ?: Typeface.DEFAULT
        view.textSize = 16f
        view.hint = hint
        view.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        view.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        view.setPadding(dp(13f), dp(9f), dp(13f), dp(9f))
        view.background = roundDrawable(theme.surfaceAlt, 0xFF352D3F.toInt(), 12)
        view.setSingleLine(!multiline)
        view.gravity = if (multiline) Gravity.TOP or Gravity.RIGHT else Gravity.CENTER_VERTICAL or Gravity.RIGHT
        view.highlightColor = selectionHighlightColor()
        if (Build.VERSION.SDK_INT >= 29) {
            view.setTextSelectHandle(SelectionHandleDrawable(activity, theme.accent, 0))
            view.setTextSelectHandleLeft(SelectionHandleDrawable(activity, theme.accent, 1))
            view.setTextSelectHandleRight(SelectionHandleDrawable(activity, theme.accent, -1))
        }
        return view
    }

    fun selectionHighlightColor(): Int = Color.argb(
        0x88,
        Color.red(theme.accent),
        Color.green(theme.accent),
        Color.blue(theme.accent)
    )

    fun iconButton(icon: String, color: Int, selected: Boolean, action: Runnable?): FrameLayout =
        makeIconButton(icon, color, selected, action, true)

    fun plainIconButton(icon: String, color: Int, action: Runnable?): FrameLayout =
        makeIconButton(icon, color, false, action, false)

    fun pairedEditCheckedButton(color: Int, action: Runnable?): FrameLayout {
        val button = FrameLayout(activity)
        button.setBackgroundColor(Color.TRANSPARENT)
        val icons = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val pencil = NativeIconView(activity, "edit", color)
        val check = NativeIconView(activity, "check", color)
        val pencilParams = LinearLayout.LayoutParams(dp(17f), dp(17f)).apply { rightMargin = dp(1f) }
        icons.addView(pencil, pencilParams)
        icons.addView(check, LinearLayout.LayoutParams(dp(17f), dp(17f)))
        button.addView(icons, FrameLayout.LayoutParams(dp(36f), dp(24f), Gravity.CENTER))
        button.contentDescription = "نشانگر پایان Chapter"
        button.isClickable = true
        button.isFocusable = true
        applyPressScale(button)
        action?.let { clickAction -> button.setOnClickListener { clickAction.run() } }
        return button
    }

    private fun makeIconButton(
        icon: String,
        color: Int,
        selected: Boolean,
        action: Runnable?,
        circular: Boolean
    ): FrameLayout {
        val button = FrameLayout(activity)
        if (circular) styleIconButton(button, selected) else button.setBackgroundColor(Color.TRANSPARENT)
        val glyph: View = if (icon == "exclamation") {
            text("!", 24, color).apply { gravity = Gravity.CENTER }
        } else NativeIconView(activity, icon, color)
        button.addView(glyph, FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER))
        button.contentDescription = iconDescription(icon)
        button.isClickable = true
        button.isFocusable = true
        applyPressScale(button)
        action?.let { clickAction -> button.setOnClickListener { clickAction.run() } }
        return button
    }

    fun weightedIconButton(icon: String, action: Runnable?): FrameLayout = FrameLayout(activity).apply {
        addView(iconButton(icon, theme.accent, false, action),
            FrameLayout.LayoutParams(dp(52f), dp(52f), Gravity.CENTER))
    }

    fun styleIconButton(button: FrameLayout, selected: Boolean) {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (selected) 0xFF30203D.toInt() else theme.surface)
            setStroke(dp(1f), themedStrokeColor(if (selected) theme.accent else 0xFF302A38.toInt()))
        }
        button.background = background
    }

    fun textButton(label: String, icon: String, color: Int, action: Runnable): View {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        row.addView(NativeIconView(activity, icon, color), LinearLayout.LayoutParams(dp(22f), dp(22f)))
        val labelView = text(label, 15, theme.text).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        }
        row.addView(labelView, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12f) })
        row.setPadding(dp(14f), 0, dp(14f), 0)
        row.background = roundDrawable(theme.surface, 0xFF302A38.toInt(), 14)
        row.setOnClickListener { action.run() }
        row.isClickable = true
        row.isFocusable = true
        applyPressScale(row)
        return row
    }

    fun applyPressScale(view: View) = TouchFeedback.applyPressScale(view, .96f)

    fun applyPressScale(view: View, pressedScale: Float) = TouchFeedback.applyPressScale(view, pressedScale)

    fun iconDescription(icon: String): String = when (icon) {
        "menu" -> "منو"
        "add" -> "افزودن"
        "filter" -> "فیلتر"
        "back" -> "بازگشت"
        "next" -> "بعدی"
        "help" -> "راهنمای توالی‌های ویژه"
        "undo" -> "بازگردانی ویرایش"
        "redo" -> "بازانجام ویرایش"
        "edit" -> "ویرایش"
        "hide" -> "مخفی کردن کتاب"
        "show" -> "نمایش کتاب"
        "delete" -> "حذف"
        "image" -> "تصویر جلد"
        "settings" -> "تنظیمات"
        "favorite" -> "افزودن به علاقه‌مندی‌ها"
        "select" -> "انتخاب کتاب"
        "exclamation" -> "جزئیات مجموعه"
        else -> icon
    }

    fun clearableInput(input: EditText): View {
        val field = FrameLayout(activity)
        field.layoutDirection = input.layoutDirection
        input.setPadding(dp(46f), input.paddingTop, input.paddingRight, input.paddingBottom)
        field.addView(input, FrameLayout.LayoutParams(-1, -1))
        val clear = FrameLayout(activity)
        clear.contentDescription = "پاک کردن متن"
        clear.isClickable = true
        clear.isFocusable = true
        clear.addView(NativeIconView(activity, "close", theme.muted),
            FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
        clear.setOnClickListener {
            input.setText("")
            input.requestFocus()
        }
        applyPressScale(clear)
        field.addView(clear, FrameLayout.LayoutParams(dp(42f), -1, Gravity.LEFT or Gravity.CENTER_VERTICAL))
        return field
    }

    fun roundDrawable(color: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        val drawable = GradientDrawable()
        when {
            color == theme.accent && theme.catalog.isGradientTheme(theme.accentIndex) ->
                theme.catalog.applyGradient(drawable, theme.accentIndex, theme.accent, dp(260f))
            color == theme.secondaryAccent && theme.catalog.isGradientTheme(theme.accentIndex) ->
                theme.catalog.applySecondaryGradient(drawable, theme.accentIndex, theme.secondaryAccent)
            else -> drawable.setColor(color)
        }
        drawable.cornerRadius = dp(radiusDp.toFloat()).toFloat()
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1f), themedStrokeColor(stroke))
        return drawable
    }

    fun themedStrokeColor(color: Int): Int {
        if (!theme.light || color == theme.accent) return color
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        return if (maximum < 115 && maximum - minimum < 75) Color.rgb(216, 213, 221) else color
    }

    fun fieldParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(48f)).apply {
        bottomMargin = dp(9f)
    }

    fun dialogPadding(child: View): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        addView(child, LinearLayout.LayoutParams(-1, -2))
    }

    fun styleDialogWindow(dialog: AlertDialog) {
        val window = dialog.window
        if (window != null) {
            window.setBackgroundDrawable(roundDrawable(theme.surface,
                if (theme.light) 0xFFD8D2E0.toInt() else 0xFF352D3F.toInt(), 20))
            window.setGravity(Gravity.CENTER)
            val width = maxOf(dp(240f), activity.resources.displayMetrics.widthPixels - dp(36f))
            window.setLayout(minOf(width, dp(460f)), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
        val titleId = dialog.context.resources.getIdentifier("alertTitle", "id", "android")
        val title = if (titleId == 0) null else dialog.findViewById<TextView>(titleId)
        title?.let {
            it.setTextColor(theme.text)
            it.typeface = theme.typeface ?: Typeface.DEFAULT
            it.gravity = Gravity.CENTER
        }
        dialog.findViewById<TextView>(android.R.id.message)?.let {
            it.setTextColor(theme.text)
            it.typeface = theme.typeface ?: Typeface.DEFAULT
            it.gravity = Gravity.CENTER
        }
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            dialog.getButton(which)?.let {
                it.setTextColor(theme.accent)
                it.typeface = theme.typeface ?: Typeface.DEFAULT
                applyPressScale(it, .98f)
            }
        }
        val buttonPanelId = dialog.context.resources.getIdentifier("buttonPanel", "id", "android")
        val panel = if (buttonPanelId == 0) null else dialog.findViewById<View>(buttonPanelId)
        if (panel is LinearLayout) panel.gravity = Gravity.CENTER_HORIZONTAL
        if (window != null) applyDialogTypeface(window.decorView)
    }

    fun applyDialogTypeface(view: View) {
        if (view is TextView) view.typeface = theme.typeface ?: Typeface.DEFAULT
        if (view !is ViewGroup) return
        for (i in 0 until view.childCount) applyDialogTypeface(view.getChildAt(i))
    }
}
