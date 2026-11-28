package com.novelreader.app

import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Renders an author's details and edit dialog. Data changes remain owned by MainActivity. */
internal class AuthorInfoScreen(private val host: MainActivity) {
    fun showAuthors() {
        host.setPage("authors")
        host.selectedAuthorNames.clear()
        host.ensureAuthorDetails()
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader("کتابخانه نویسندگان", "", "menu", "help",
                Runnable { host.openDrawer() }, Runnable { showLibraryHelp() }, 0, 68, true),
            LinearLayout.LayoutParams(-1, host.dp(62f))
        )

        val authorSearch = host.edit("جستجوی نویسنده", false).apply { isSingleLine = true }
        host.authorSearchField = authorSearch
        val filters = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(14f), host.dp(7f), host.dp(14f), host.dp(7f))
            addView(host.clearableInput(authorSearch), LinearLayout.LayoutParams(-1, host.dp(48f)))
        }
        host.appColumn.addView(filters)

        val grid = GridView(host).apply {
            numColumns = 1
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            horizontalSpacing = host.dp(10f)
            verticalSpacing = host.dp(10f)
            setSelector(android.R.color.transparent)
            cacheColorHint = android.graphics.Color.TRANSPARENT
            setBackgroundColor(host.BG)
            setPadding(host.dp(12f), host.dp(4f), host.dp(12f), host.dp(8f))
            clipToPadding = false
        }
        val adapter = AuthorCardAdapter(host)
        host.authorAdapter = adapter
        host.authorsGrid = grid
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val author = adapter.getAuthor(position)
            if (host.selectedAuthorNames.isNotEmpty()) host.toggleAuthorSelection(author)
            else host.selectAuthorCard(author)
        }
        grid.setOnItemLongClickListener { _, view, position, _ ->
            val author = adapter.getAuthor(position)
            if (host.containsIgnoreCase(host.selectedAuthorNames, author)) {
                host.startReorderDrag(view, "author", author)
            } else {
                host.toggleAuthorSelection(author)
                true
            }
        }

        val frame = FrameLayout(host).apply {
            addView(grid, FrameLayout.LayoutParams(-1, -1))
            setBackgroundColor(host.BG)
        }
        val empty = host.emptyLabel("کتابی با نام نویسنده ثبت نشده")
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        grid.emptyView = empty
        host.appColumn.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        authorSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) = adapter.notifyDataSetChanged()
        })
    }

    fun showLibraryHelp() {
        val help = host.text(
            "برای دیدن جزئیات، روی کارت نویسنده بزن. دکمهٔ کتاب‌های این نویسنده، کتابخانهٔ اصلی را با جستجوی همان نویسنده باز می‌کند. قلب، نویسنده را به علاقه‌مندی‌ها اضافه می‌کند و آیکون کارت برای تعیین جنسیت است.",
            15, host.TEXT
        ).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(host.dp(10f), host.dp(12f), host.dp(10f), host.dp(12f))
        }
        val dialog = AlertDialog.Builder(host).setTitle("کتابخانه نویسندگان")
            .setView(host.dialogPadding(help)).setPositiveButton("بستن", null).create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    fun refreshSelection() {
        if (host.page != "authors" || host.appColumn.childCount == 0) return
        host.appColumn.removeViewAt(0)
        val header = if (host.selectedAuthorNames.isEmpty()) {
            host.makeHeader("کتابخانه نویسندگان", "", "menu", "help",
                Runnable { host.openDrawer() }, Runnable { showLibraryHelp() }, 0, 68, true)
        } else {
            host.makeHeader("${host.selectedAuthorNames.size} انتخاب", "", "close", null,
                Runnable { clearSelection() }, null)
        }
        host.appColumn.addView(header, 0, LinearLayout.LayoutParams(-1, host.dp(62f)))
        host.authorAdapter?.notifyDataSetChanged()
    }

    fun clearSelection() {
        host.selectedAuthorNames.clear()
        refreshSelection()
    }

    fun showPage(author: String) {
        if (author.trim().isEmpty()) return
        val currentAuthor = author.trim()
        val detail = host.ensureAuthorDetail(currentAuthor)
        host.setPage("authorInfo")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader(currentAuthor, "", "menu", "edit", Runnable { host.openDrawer() },
                Runnable { showEditDialog(currentAuthor) }, 10),
            LinearLayout.LayoutParams(-1, host.dp(62f))
        )
        host.titleView?.apply {
            contentDescription = "کپی نام نویسنده"
            setOnClickListener { host.copyToClipboard("author", currentAuthor) }
        }

        val scroll = ScrollView(host).apply { isVerticalScrollBarEnabled = false }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(18f), host.dp(18f), host.dp(24f))
        }
        val iconBox = FrameLayout(host).apply {
            background = host.roundDrawable(host.SURFACE, 0xFF40384A.toInt(), 22)
            addView(NativeIconView(host, host.authorIcon(currentAuthor), host.PURPLE),
                FrameLayout.LayoutParams(host.dp(60f), host.dp(60f), Gravity.CENTER))
            contentDescription = "انتخاب جنسیت نویسنده"
            setOnClickListener {
                showGenderPicker(host.authorGender(currentAuthor)) { gender ->
                    host.setAuthorGender(currentAuthor, gender)
                    showPage(currentAuthor)
                }
            }
        }
        val iconParams = LinearLayout.LayoutParams(host.dp(88f), host.dp(88f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = host.dp(12f)
        }
        body.addView(iconBox, iconParams)

        body.addView(host.textButton(
            "جنسیت: ${genderLabel(host.authorGender(currentAuthor))}",
            genderIcon(host.authorGender(currentAuthor)), host.PURPLE
        ) {
            showGenderPicker(host.authorGender(currentAuthor)) { gender ->
                host.setAuthorGender(currentAuthor, gender)
                showPage(currentAuthor)
            }
        }, LinearLayout.LayoutParams(-1, host.dp(46f)).apply { bottomMargin = host.dp(10f) })

        val feedback = LinearLayout(host).apply {
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val liked = host.containsIgnoreCase(host.favoriteAuthorPreferences, currentAuthor)
        val disliked = host.containsIgnoreCase(host.dislikedAuthorPreferences, currentAuthor)
        feedback.addView(host.plainIconButton("thumb_up", if (liked) MainActivity.GREEN else host.MUTED,
            Runnable {
                host.toggleFavoriteAuthor(currentAuthor)
                showPage(currentAuthor)
            }), LinearLayout.LayoutParams(host.dp(48f), host.dp(42f)))
        feedback.addView(host.plainIconButton("thumb_down", if (disliked) MainActivity.RED else host.MUTED,
            Runnable {
                host.toggleDislikedAuthor(currentAuthor)
                showPage(currentAuthor)
            }), LinearLayout.LayoutParams(host.dp(48f), host.dp(42f)))
        body.addView(feedback, LinearLayout.LayoutParams(-1, host.dp(46f)).apply {
            bottomMargin = host.dp(12f)
        })

        val descriptionValue = detail?.optString("description", "")?.trim().orEmpty()
        body.addView(host.text("توضیحات کوتاه", 14, host.MUTED).apply { gravity = Gravity.RIGHT })
        body.addView(host.text(
            if (descriptionValue.isEmpty()) "توضیحی ثبت نشده" else descriptionValue,
            15,
            if (descriptionValue.isEmpty()) host.MUTED else host.TEXT
        ).apply {
            gravity = Gravity.RIGHT
            setPadding(0, host.dp(6f), 0, host.dp(18f))
        })

        host.addBookInfoLine(body, "تعداد کتاب", host.authorBookCount(currentAuthor).toString())
        var readingMillis = 0L
        val books: JSONArray? = host.library.optJSONArray("books")
        if (books != null) {
            for (index in 0 until books.length()) {
                val book: JSONObject = books.optJSONObject(index) ?: continue
                if (!currentAuthor.equals(book.optString("author", "").trim(), ignoreCase = true)) continue
                readingMillis += host.readingStats?.totalForBook(book.optString("id")) ?: 0L
            }
        }
        host.addBookInfoLine(body, "زمان مطالعه", host.formatReadingTime(readingMillis))
        body.addView(host.textButton("کتاب‌های این نویسنده", "book", host.PURPLE,
            Runnable { host.showAllBooksForAuthor(currentAuthor) }),
            LinearLayout.LayoutParams(-1, host.dp(50f)))
        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun showEditDialog(originalName: String) {
        val name = host.edit("نام نویسنده", false).apply {
            setText(originalName)
            setSelection(length())
        }
        val description = host.edit("توضیحات کوتاه", true)
        val authorDetail = host.ensureAuthorDetail(originalName)
        if (authorDetail != null) description.setText(authorDetail.optString("description", ""))

        val genders = arrayOf("unknown", "female", "male")
        val currentGender = host.authorGender(originalName)
        val selectedGender = intArrayOf(when (currentGender) {
            "female" -> 1
            "male" -> 2
            else -> 0
        })
        val genderButtonRef = arrayOfNulls<LinearLayout>(1)
        val genderButton = host.textButton(
            "جنسیت: ${genderLabel(genders[selectedGender[0]])}",
            genderIcon(genders[selectedGender[0]]), host.PURPLE
        ) {
            showGenderPicker(genders[selectedGender[0]]) { chosen ->
                selectedGender[0] = genders.indexOf(chosen).coerceAtLeast(0)
                updateGenderButton(genderButtonRef[0]!!, genders[selectedGender[0]])
            }
        }
        genderButtonRef[0] = genderButton

        val form = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(10f), host.dp(18f), host.dp(8f))
        }
        form.addView(host.clearableInput(name), host.fieldParams())
        form.addView(host.clearableInput(description), host.fieldParams().apply {
            topMargin = host.dp(8f)
        })
        form.addView(genderButton, host.fieldParams().apply { topMargin = host.dp(8f) })

        val formScroll = ScrollView(host).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(form)
        }
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = host.dp(410f)
            addView(formScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val footer = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(host.dp(18f), host.dp(6f), host.dp(18f), host.dp(12f))
        }
        val cancel = host.text("لغو", 15, host.MUTED).apply {
            gravity = Gravity.CENTER
            background = host.roundDrawable(host.SURFACE, host.themedStrokeColor(0xFF40384A.toInt()), 12)
        }
        footer.addView(cancel, LinearLayout.LayoutParams(0, host.dp(46f), 1f))
        footer.addView(View(host), LinearLayout.LayoutParams(host.dp(12f), 1))
        val save = host.text("ذخیره", 15, host.contrastOnAccent()).apply {
            gravity = Gravity.CENTER
            background = host.roundDrawable(host.PURPLE, host.PURPLE, 12)
        }
        footer.addView(save, LinearLayout.LayoutParams(0, host.dp(46f), 1f))
        content.addView(footer, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(host).setTitle("اطلاعات نویسنده").setView(content).create()
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val newName = TextNormalization.titleCaseWords(name.text.toString())
            if (newName.isEmpty()) {
                name.error = "نام نویسنده را وارد کن"
                return@setOnClickListener
            }
            if (!host.renameAuthor(originalName, newName, genders[selectedGender[0]])) return@setOnClickListener
            val savedAuthor = host.ensureAuthorDetail(newName)
            if (savedAuthor != null) host.putJson(savedAuthor, "description", description.text.toString().trim())
            if (!host.saveLibrary()) return@setOnClickListener
            dialog.dismiss()
            if (host.page == "authorInfo") showPage(newName)
        }
        host.applyPressScale(cancel, .97f)
        host.applyPressScale(save, .97f)
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val availableHeight = maxOf(host.dp(360f), host.resources.displayMetrics.heightPixels - host.dp(180f))
            setLayout(minOf(host.resources.displayMetrics.widthPixels - host.dp(32f), host.dp(480f)),
                minOf(host.dp(590f), availableHeight))
        }
    }

    private fun showGenderPicker(initial: String, onSelected: (String) -> Unit) {
        val choices = arrayOf("unknown", "female", "male")
        val labels = arrayOf("نامشخص", "زن", "مرد")
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(host.dp(16f), host.dp(8f), host.dp(16f), host.dp(8f))
        }
        val dialog = AlertDialog.Builder(host).setTitle("انتخاب جنسیت").setView(content).create()
        choices.forEachIndexed { index, gender ->
            val row = host.textButton(labels[index], genderIcon(gender),
                if (gender == initial) host.PURPLE else host.SECONDARY_ACCENT) {
                dialog.dismiss()
                onSelected(gender)
            }
            content.addView(row, LinearLayout.LayoutParams(-1, host.dp(48f)).apply {
                bottomMargin = if (index == choices.lastIndex) 0 else host.dp(7f)
            })
        }
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    private fun genderLabel(gender: String): String = when (gender) {
        "female" -> "زن"
        "male" -> "مرد"
        else -> "نامشخص"
    }

    private fun genderIcon(gender: String): String = when (gender) {
        "female" -> "author_woman"
        "male" -> "author_man"
        else -> "author_unknown"
    }

    private fun updateGenderButton(button: LinearLayout, gender: String) {
        (button.getChildAt(1) as? TextView)?.text = "جنسیت: ${genderLabel(gender)}"
        val oldIcon = button.getChildAt(0) ?: return
        val params = oldIcon.layoutParams
        button.removeViewAt(0)
        button.addView(NativeIconView(host, genderIcon(gender), host.PURPLE), 0, params)
    }
}
