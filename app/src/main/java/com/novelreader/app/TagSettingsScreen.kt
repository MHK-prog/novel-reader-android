package com.novelreader.app

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.util.Locale

/** Builds the genre preference list and its add/search controls. */
internal class TagSettingsScreen(private val host: MainActivity) {
    fun show() {
        host.setPage("tagSettings")
        host.ensureFavoriteTagsDefined()
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(host.makeHeader("تنظیمات ژانرها", "", "menu", "help",
            Runnable { host.openDrawer() }, Runnable { host.showTagSettingsHelp() }, 0, 68, true))

        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(14f), host.dp(18f), host.dp(24f))
        }
        val search = host.edit("جستجو یا افزودن ژانر", false).apply { isSingleLine = true }
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val searchRow = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        searchRow.addView(host.clearableInput(search), LinearLayout.LayoutParams(0, host.dp(48f), 1f))
        val addButton = host.iconButton("add", host.PURPLE, false, Runnable { addManagedTag(search, rows) })
        searchRow.addView(addButton, LinearLayout.LayoutParams(host.dp(48f), host.dp(48f)).apply {
            leftMargin = host.dp(7f)
        })
        body.addView(searchRow, host.fieldParams())
        body.addView(rows, LinearLayout.LayoutParams(-1, -2))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) = renderManagedTags(rows, search)
        })
        renderManagedTags(rows, search)

        body.addView(host.textButton("ژانرهای معادل", "tag", host.PURPLE,
            Runnable { host.showGenreEquivalents() }),
            LinearLayout.LayoutParams(-1, host.dp(48f)).apply { topMargin = host.dp(8f) })
        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun renderManagedTags(rows: LinearLayout, search: EditText) {
        rows.removeAllViews()
        val query = search.text.toString().trim().lowercase(Locale.ROOT)
        val tags = host.allGenres()
        for (index in 0 until tags.length()) {
            val tag = tags.optString(index)
            if (!tag.lowercase(Locale.ROOT).contains(query)) continue
            val row = LinearLayout(host).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(host.dp(10f), host.dp(3f), host.dp(10f), host.dp(3f))
                background = host.roundDrawable(host.SURFACE, 0xFF302A38.toInt(), 12)
            }
            val params = LinearLayout.LayoutParams(-1, host.dp(54f)).apply {
                bottomMargin = host.dp(7f)
            }
            row.addView(host.text(tag, 15, host.TEXT).apply {
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -1, 1f))

            val edit = host.iconButton("edit", host.MUTED, false,
                Runnable { host.showEditTagDialog(tag, rows, search) })
            edit.contentDescription = "ویرایش یا ادغام ژانر"
            row.addView(edit, LinearLayout.LayoutParams(host.dp(38f), host.dp(42f)))
            rows.addView(row, params)
        }
        if (rows.childCount == 0) {
            val message = if (query.isEmpty()) "هنوز ژانری ثبت نشده" else "ژانری پیدا نشد"
            rows.addView(host.emptyLabel(message), LinearLayout.LayoutParams(-1, host.dp(80f)))
        }
    }

    fun addManagedTag(search: EditText, rows: LinearLayout) {
        val tag = host.canonicalGenre(TextNormalization.titleCaseWords(search.text.toString()))
        if (tag.isEmpty()) {
            search.error = "نام ژانر را وارد کن"
            return
        }
        val tags = host.library.optJSONArray("genres") ?: JSONArray()
        if (host.findGenreName(tags, tag) != null) {
            search.setText("")
            host.toast("این نام به ژانر «$tag» تبدیل می‌شود.")
            renderManagedTags(rows, search)
            return
        }
        host.appendUniqueGenre(tags, tag)
        host.putJson(host.library, "genres", tags)
        if (!host.saveLibrary()) return
        host.rebuildRecommendationWeights()
        host.persistRecommendationState()
        search.setText("")
        renderManagedTags(rows, search)
    }
}
