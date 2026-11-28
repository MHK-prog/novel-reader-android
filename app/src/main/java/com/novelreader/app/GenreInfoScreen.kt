package com.novelreader.app

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Builds the genre detail page; genre edits and persistence stay in MainActivity. */
internal class GenreInfoScreen(private val host: MainActivity) {
    fun showPage(genre: String) {
        val currentGenre = genre.trim()
        if (currentGenre.isEmpty()) return
        val detail = host.ensureGenreDetail(currentGenre)
        host.setPage("genreInfo")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader(currentGenre, "", "menu", "edit", Runnable { host.openDrawer() },
                Runnable { host.showEditGenreInfoDialog(currentGenre) }, 10),
            LinearLayout.LayoutParams(-1, host.dp(62f))
        )
        host.titleView?.apply {
            contentDescription = "کپی نام ژانر"
            setOnClickListener { host.copyToClipboard("genre", currentGenre) }
        }

        val scroll = ScrollView(host).apply { isVerticalScrollBarEnabled = false }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(18f), host.dp(18f), host.dp(24f))
        }
        host.addBookInfoLine(body, "تعداد کتاب", host.genreBookCount(currentGenre).toString())

        val descriptionValue = detail?.optString("description", "")?.trim().orEmpty()
        body.addView(host.text("توضیحات کوتاه", 14, host.MUTED).apply { gravity = Gravity.RIGHT })
        body.addView(host.text(
            if (descriptionValue.isEmpty()) "توضیحی ثبت نشده" else descriptionValue,
            15,
            if (descriptionValue.isEmpty()) host.MUTED else host.TEXT
        ).apply {
            gravity = Gravity.RIGHT
            setPadding(0, host.dp(6f), 0, host.dp(16f))
            setLineSpacing(host.dp(2f).toFloat(), 1.35f)
            typeface = host.vazir
        })
        body.addView(host.textButton("کتاب‌های این ژانر", "book", host.PURPLE,
            Runnable { host.showAllBooksForGenre(currentGenre) }),
            LinearLayout.LayoutParams(-1, host.dp(50f)))
        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }
}
