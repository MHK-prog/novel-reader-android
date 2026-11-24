package com.novelreader.app

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Builds general and theme settings screens; preference values stay in Android SharedPreferences. */
internal class SettingsScreen(private val host: MainActivity) {
    fun showSettings() {
        host.setPage("settings")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader("تنظیمات", "", "menu", null, Runnable { host.openDrawer() }, null)
        )
        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        host.settingsScroll = scroll
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(14f), host.dp(18f), host.dp(24f))
        }
        body.addView(host.text("ذخیره‌سازی", 15, host.MUTED), host.fieldParams())
        body.addView(host.textButton("انتخاب پوشهٔ ذخیره‌سازی", "folder", host.PURPLE) {
            host.returnToSettingsAfterPicker = true
            host.chooseFolder()
        }, host.fieldParams())

        val displayTitle = host.text("اطلاعات بالای صفحهٔ مطالعه", 15, host.MUTED).apply {
            setPadding(0, host.dp(15f), 0, host.dp(7f))
        }
        body.addView(displayTitle)
        addReaderDisplaySwitch(body, "نمایش درصد باتری", "show_battery")
        addReaderDisplaySwitch(body, "نمایش ساعت", "show_clock")

        val sortTitle = host.text("مرتب‌سازی کتابخانه", 15, host.MUTED).apply {
            setPadding(0, host.dp(16f), 0, host.dp(7f))
        }
        body.addView(sortTitle)
        body.addView(host.textButton("مرتب‌سازی بر اساس: ${bookSortLabel()}", "filter", host.PURPLE) {
            showBookSortDialog()
        }, host.fieldParams())
        addReadingSpeedSetting(body)

        val bookTitle = host.text("افزودن کتاب", 15, host.MUTED).apply {
            setPadding(0, host.dp(10f), 0, host.dp(7f))
        }
        body.addView(bookTitle)
        addPreferenceSwitch(body, "به هوش مصنوعی دسترسی ندارم؛ نمایش کامل فرم", "general", "no_ai_access", false)
        body.addView(host.textButton("ویرایش متن پرایمت ورود JSON", "edit", host.PURPLE) {
            host.showBookJsonPromptSettings()
        }, host.fieldParams())
        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun showThemes() {
        host.setPage("themes")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader("تم‌ها", "", "menu", null, Runnable { host.openDrawer() }, null)
        )
        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        host.settingsScroll = scroll
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(14f), host.dp(18f), host.dp(24f))
        }
        body.addView(host.text("حالت نمایش", 15, host.MUTED).apply {
            setPadding(0, 0, 0, host.dp(7f))
        })
        val modes = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        modes.addView(themeChoice("سیاه", !host.lightTheme, false), LinearLayout.LayoutParams(0, host.dp(48f), 1f))
        modes.addView(View(host), LinearLayout.LayoutParams(host.dp(10f), 1))
        modes.addView(themeChoice("روشن", host.lightTheme, true), LinearLayout.LayoutParams(0, host.dp(48f), 1f))
        body.addView(modes, host.fieldParams())
        addPreferenceSwitch(body, "انتخاب تصادفی از تم‌های پسندیده هنگام ورود", "appearance",
            "random_each_launch", false)

        body.addView(host.text("همهٔ تم‌ها", 15, host.MUTED).apply {
            setPadding(0, host.dp(9f), 0, host.dp(7f))
        })
        val grid = GridLayout(host).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        for (index in 0 until host.themeCatalog.themeCount()) {
            val screenWidthDp = (host.resources.displayMetrics.widthPixels / host.resources.displayMetrics.density).toInt()
            val widthDp = maxOf(132, (screenWidthDp - 48) / 2)
            val params = GridLayout.LayoutParams().apply {
                width = host.dp(widthDp.toFloat())
                height = host.dp(56f)
                setMargins(host.dp(3f), host.dp(3f), host.dp(3f), host.dp(3f))
            }
            grid.addView(themeCard(index), params)
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
        body.addView(host.textButton("افزودن تم جدید", "add", host.PURPLE) {
            showAddThemeDialog()
        }, LinearLayout.LayoutParams(-1, host.dp(50f)).apply { topMargin = host.dp(11f) })
        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun addPreferenceSwitch(
        body: LinearLayout,
        label: String,
        preferenceFile: String,
        key: String,
        defaultValue: Boolean,
    ) {
        val preferences = host.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        val row = switchRow(label, preferences.getBoolean(key, defaultValue)) { checked ->
            preferences.edit().putBoolean(key, checked).apply()
            if (key == "enabled.colloquializer" && host.page == "reader") host.showReader()
        }
        body.addView(row, LinearLayout.LayoutParams(-1, host.dp(52f)).apply {
            bottomMargin = host.dp(8f)
        })
    }

    private fun addReaderDisplaySwitch(body: LinearLayout, label: String, key: String) {
        val preferences = host.getSharedPreferences("reader_display", Context.MODE_PRIVATE)
        val row = switchRow(label, preferences.getBoolean(key, true)) { checked ->
            preferences.edit().putBoolean(key, checked).apply()
        }
        body.addView(row, LinearLayout.LayoutParams(-1, host.dp(52f)).apply {
            bottomMargin = host.dp(8f)
        })
    }

    private fun switchRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(host.dp(14f), 0, host.dp(12f), 0)
            background = host.roundDrawable(host.SURFACE, 0xFF302A38.toInt(), 12)
        }
        row.addView(host.text(label, 14, host.TEXT), LinearLayout.LayoutParams(0, -1, 1f))
        val toggle = Switch(host).apply {
            isChecked = checked
            if (Build.VERSION.SDK_INT >= 21) {
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                thumbTintList = ColorStateList(states, intArrayOf(host.PURPLE, host.MUTED))
                trackTintList = ColorStateList(states, intArrayOf(
                    Color.argb(100, Color.red(host.PURPLE), Color.green(host.PURPLE), Color.blue(host.PURPLE)),
                    0xFF625D66.toInt()
                ))
            }
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        host.applyPressScale(toggle, .98f)
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        host.applyPressScale(row, .98f)
        return row
    }

    private fun addReadingSpeedSetting(body: LinearLayout) {
        body.addView(host.text("سرعت مطالعه (کلمه در دقیقه)", 14, host.MUTED).apply {
            setPadding(0, host.dp(14f), 0, host.dp(6f))
        })
        val preferences = host.getSharedPreferences("general", Context.MODE_PRIVATE)
        val speed = host.edit("200", false).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
            setText(preferences.getInt("reading_speed_wpm", 200).toString())
            setSelection(length())
        }
        speed.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) {
                val wordsPerMinute = value?.toString()?.trim()?.toIntOrNull() ?: return
                if (wordsPerMinute > 0) preferences.edit().putInt("reading_speed_wpm", wordsPerMinute).apply()
            }
        })
        body.addView(host.clearableInput(speed), host.fieldParams())
    }

    private fun bookSortLabel(): String = when (
        host.getSharedPreferences("general", Context.MODE_PRIVATE).getString("book_sort", "recent")
    ) {
        "title" -> "عنوان"
        "manual" -> "ترتیب دستی"
        else -> "آخرین مطالعه"
    }

    fun showBookSortDialog() {
        val labels = arrayOf("آخرین مطالعه", "عنوان", "ترتیب دستی")
        val values = arrayOf("recent", "title", "manual")
        val preferences = host.getSharedPreferences("general", Context.MODE_PRIVATE)
        val current = preferences.getString("book_sort", "recent")
        val checked = when (current) { "title" -> 1; "manual" -> 2; else -> 0 }
        AlertDialog.Builder(host).setTitle("مرتب‌سازی بر اساس")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                preferences.edit().putString("book_sort", values[which]).apply()
                dialog.dismiss()
                showSettings()
            }
            .setNegativeButton("لغو", null)
            .create().also { it.show(); host.styleDialogWindow(it) }
    }

    private fun themeCard(index: Int): View {
        val card = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(host.dp(8f), host.dp(5f), host.dp(8f), host.dp(5f))
            background = host.roundDrawable(host.SURFACE,
                if (index == host.accentIndex) host.PURPLE else 0xFF40384A.toInt(), 12)
            isClickable = true
            isFocusable = true
        }
        val favorite = FrameLayout(host).apply {
            contentDescription = "پسندیدن تم"
            isClickable = true
            isFocusable = true
        }
        val liked = host.themeCatalog.isFavorite(index)
        favorite.addView(NativeIconView(host, if (liked) "heart_filled" else "heart_outline",
            if (liked) MainActivity.RED else host.MUTED), FrameLayout.LayoutParams(
            host.dp(20f), host.dp(20f), Gravity.CENTER
        ))
        host.applyPressScale(favorite, .98f)
        favorite.setOnClickListener {
            host.themeCatalog.setFavorite(index, !host.themeCatalog.isFavorite(index))
            host.rerenderCurrentPage()
        }
        card.addView(favorite, LinearLayout.LayoutParams(host.dp(30f), host.dp(40f)))

        val preview = GradientDrawable().apply {
            cornerRadius = host.dp(9f).toFloat()
            host.themeCatalog.applyGradient(this, index, host.PURPLE, host.dp(19f))
        }
        card.addView(FrameLayout(host).apply { background = preview },
            LinearLayout.LayoutParams(host.dp(30f), host.dp(30f)))
        val label = host.text(host.themeCatalog.themeName(index), 12, host.TEXT).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        card.addView(label, LinearLayout.LayoutParams(0, -1, 1f).apply {
            rightMargin = host.dp(4f)
        })
        card.setOnClickListener {
            host.accentIndex = index
            host.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putInt("accent", host.accentIndex).apply()
            host.applyThemeColors()
            host.rerenderCurrentPage()
        }
        host.applyPressScale(card, .97f)
        return card
    }

    private fun showAddThemeDialog() {
        val form = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(4f), host.dp(4f), host.dp(4f), host.dp(4f))
        }
        val name = host.edit("نام تم", false)
        val colors = host.edit("دو رنگ با ویرگول: #B69AFF, #7046C8", false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        form.addView(name, host.fieldParams())
        form.addView(colors, host.fieldParams())
        val dialog = AlertDialog.Builder(host).setTitle("تم جدید")
            .setView(host.dialogPadding(form)).setPositiveButton("افزودن", null)
            .setNegativeButton("لغو", null).create()
        dialog.setOnShowListener {
            host.styleDialogWindow(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val themeName = name.text.toString().trim()
                val rawColors = colors.text.toString().split(',')
                if (themeName.isEmpty() || rawColors.size != 2) {
                    host.toast("نام تم و دقیقاً دو رنگ وارد کن.")
                    return@setOnClickListener
                }
                val parsedColors = JSONArray()
                try {
                    rawColors.forEach { raw ->
                        var value = raw.trim()
                        if (!value.startsWith("#")) value = "#$value"
                        parsedColors.put(Color.parseColor(value))
                    }
                    val themes = host.themeCatalog.customThemes()
                    val theme = JSONObject().put("name", themeName).put("colors", parsedColors).put("favorite", false)
                    themes.put(theme)
                    host.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                        .putString("custom_themes", themes.toString()).apply()
                    host.accentIndex = ThemeCatalog.BUILTIN_THEME_COUNT + themes.length() - 1
                    host.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                        .putInt("accent", host.accentIndex).apply()
                    dialog.dismiss()
                    host.applyThemeColors()
                    host.rerenderCurrentPage()
                } catch (_: IllegalArgumentException) {
                    host.toast("رنگ را با قالب #RRGGBB وارد کن.")
                } catch (_: JSONException) {
                    host.toast("رنگ را با قالب #RRGGBB وارد کن.")
                }
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    private fun themeChoice(label: String, selected: Boolean, light: Boolean): TextView =
        host.text(label, 15, if (selected) host.contrastOnAccent() else host.TEXT).apply {
            gravity = Gravity.CENTER
            background = host.roundDrawable(
                if (selected) host.PURPLE else host.SURFACE,
                if (selected) host.PURPLE else 0xFF40384A.toInt(), 12
            )
            setOnClickListener {
                host.lightTheme = light
                host.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                    .putBoolean("light", host.lightTheme).apply()
                host.applyThemeColors()
                host.rerenderCurrentPage()
            }
            host.applyPressScale(this, .98f)
        }
}
