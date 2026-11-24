package com.novelreader.app

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Renders an author's details and edit dialog. Data changes remain owned by MainActivity. */
internal class AuthorInfoScreen(private val host: MainActivity) {
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
        }
        val iconParams = LinearLayout.LayoutParams(host.dp(88f), host.dp(88f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = host.dp(12f)
        }
        body.addView(iconBox, iconParams)

        val genderLabel = when (host.authorGender(currentAuthor)) {
            "female" -> "زن"
            "male" -> "مرد"
            else -> "نامشخص"
        }
        body.addView(host.text("جنسیت: $genderLabel", 14, host.MUTED).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, host.dp(34f)))

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
        val labels = arrayOf("نامشخص", "زن", "مرد")
        val genderSection = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        genderSection.addView(host.text("جنسیت نویسنده", 14, host.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(12f), 0, host.dp(7f))
        }, LinearLayout.LayoutParams(-1, -2))
        val options = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        labels.forEachIndexed { index, label ->
            val choice = host.text(label, 14,
                if (selectedGender[0] == index) host.contrastOnAccent() else host.TEXT).apply {
                gravity = Gravity.CENTER
                background = host.roundDrawable(
                    if (selectedGender[0] == index) host.PURPLE else host.SURFACE,
                    if (selectedGender[0] == index) host.PURPLE else 0xFF40384A.toInt(), 12
                )
                setOnClickListener {
                    selectedGender[0] = index
                    for (itemIndex in 0 until options.childCount) {
                        val item = options.getChildAt(itemIndex) as? TextView ?: continue
                        val selected = itemIndex == index
                        item.setTextColor(if (selected) host.contrastOnAccent() else host.TEXT)
                        item.background = host.roundDrawable(
                            if (selected) host.PURPLE else host.SURFACE,
                            if (selected) host.PURPLE else 0xFF40384A.toInt(), 12
                        )
                    }
                }
            }
            val params = LinearLayout.LayoutParams(0, host.dp(46f), 1f)
            if (index > 0) params.rightMargin = host.dp(7f)
            options.addView(choice, params)
            host.applyPressScale(choice, .98f)
        }
        genderSection.addView(options, LinearLayout.LayoutParams(-1, host.dp(46f)))

        val form = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(10f), host.dp(18f), host.dp(8f))
        }
        form.addView(host.clearableInput(name), host.fieldParams())
        form.addView(host.clearableInput(description), host.fieldParams().apply {
            topMargin = host.dp(8f)
        })
        form.addView(genderSection, host.fieldParams().apply { topMargin = host.dp(8f) })

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
}
