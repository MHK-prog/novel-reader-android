package com.novelreader.app

import android.app.AlertDialog
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.HashMap
import kotlin.math.roundToInt

/** Renders recommendations and their weight settings; selection rules remain in MainActivity. */
internal class RecommendationScreen(private val host: MainActivity) {
    fun show() {
        host.setPage("recommendations")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader("پیشنهاد کتاب", "", "menu", null, Runnable { host.openDrawer() }, null)
        )
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(host.dp(24f), host.dp(20f), host.dp(24f), host.dp(20f))
        }
        val availableBooks = host.recommendationBooks(
            host.recommendationCurrentReadingMode, host.sessionRejectedRecommendationIds
        )
        val book = host.recommendationEngine.recommendBook(
            availableBooks, host.seenRecommendationIds, host.temporaryRecommendationGenre
        )
        if (book == null) {
            val emptyMessage = host.text(
                if (availableBooks.isEmpty()) "کتابی برای این حالت باقی نمانده است."
                else "کتابی برای پیشنهاد باقی نمانده است.", 15, host.MUTED
            ).apply { gravity = Gravity.CENTER }
            body.addView(emptyMessage, LinearLayout.LayoutParams(-1, 0, 1f))
            body.addView(host.textButton("انتخاب ژانر محبوب", "tag", host.PURPLE) {
                host.showRecommendationGenrePicker()
            }, LinearLayout.LayoutParams(-1, host.dp(48f)))
            body.addView(host.recommendationModeButtons(), LinearLayout.LayoutParams(-1, host.dp(48f)).apply {
                topMargin = host.dp(7f)
            })
            host.appColumn.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            if (!host.recommendationGenreDialogShown) {
                host.recommendationGenreDialogShown = true
                body.post { host.showRecommendationGenrePicker() }
            }
            return
        }

        host.recommendationGenreDialogShown = false
        host.seenRecommendationIds.add(book.id)
        host.persistRecommendationState()
        val record = host.findBookByRecommendationId(book.id)
        val coverFrame = FrameLayout(host).apply {
            background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 14)
            clipToOutline = true
        }
        val cover = ImageView(host).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 14)
        }
        coverFrame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        val bookIcon = NativeIconView(host, "book", host.PURPLE)
        coverFrame.addView(bookIcon, FrameLayout.LayoutParams(host.dp(48f), host.dp(48f), Gravity.CENTER))
        if (record != null) host.bindBookCover(cover, bookIcon, record)
        coverFrame.setOnClickListener {
            if (record != null) {
                host.bookInfoReturnPage = "recommendations"
                host.showBookInfo(record)
            }
        }
        host.applyPressScale(coverFrame, .97f)
        body.addView(coverFrame, LinearLayout.LayoutParams(host.dp(184f), host.dp(238f)))

        val title = host.text(record?.let { host.displayBookTitle(it) } ?: book.title, 22, host.TEXT).apply {
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(0, host.dp(22f), 0, host.dp(10f))
        }
        body.addView(title, LinearLayout.LayoutParams(-1, -2))
        val author = host.text(book.author, 16, host.MUTED).apply { gravity = Gravity.CENTER }
        body.addView(author, LinearLayout.LayoutParams(-1, -2))

        val descriptionText = record?.optString("description", "")?.trim().orEmpty()
        val description = if (descriptionText.isEmpty()) {
            host.text("توضیحی برای این کتاب ثبت نشده است.", 14, host.MUTED)
        } else {
            val hasMore = descriptionText.length > 90
            val preview = if (hasMore) descriptionText.substring(0, 90).trim() + "… بیشتر" else descriptionText
            val styled = SpannableString(preview)
            if (hasMore) styled.setSpan(
                ForegroundColorSpan(MainActivity.RED), preview.length - 5,
                preview.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            host.text(preview, 14, host.MUTED).apply {
                text = styled
                setOnClickListener { host.showRecommendationDescription(descriptionText) }
                isClickable = true
                isFocusable = true
                host.applyPressScale(this, .98f)
            }
        }
        description.apply {
            gravity = Gravity.CENTER
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(host.dp(8f), 0, host.dp(8f), 0)
        }
        body.addView(description, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = host.dp(12f)
        })
        if (record != null) body.addView(
            host.createBookRatingControl(record), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = host.dp(8f)
            }
        )
        body.addView(View(host), LinearLayout.LayoutParams(1, 0, 1f))
        body.addView(host.textButton("خواندن کتاب", "book", host.PURPLE) {
            host.openRecommendedBookForReading(record)
        }, LinearLayout.LayoutParams(-1, host.dp(48f)).apply { topMargin = host.dp(6f) })
        body.addView(host.recommendationModeButtons(), LinearLayout.LayoutParams(-1, host.dp(48f)).apply {
            topMargin = host.dp(7f)
        })
        host.appColumn.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun modeButtons(): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        addView(host.textButton("پیشنهاد از نخوانده‌ها", "book",
            if (host.recommendationCurrentReadingMode) host.SECONDARY_ACCENT else host.PURPLE) {
            host.recommendationCurrentReadingMode = false
            host.showRecommendation()
        }, LinearLayout.LayoutParams(0, host.dp(48f), 1f))
        addView(host.textButton("پیشنهاد از درحال‌خواندن", "book",
            if (host.recommendationCurrentReadingMode) host.PURPLE else host.SECONDARY_ACCENT) {
            host.recommendationCurrentReadingMode = true
            host.showRecommendation()
        }, LinearLayout.LayoutParams(0, host.dp(48f), 1f).apply { leftMargin = host.dp(7f) })
    }

    fun showGenrePicker() {
        val top = topGenres()
        if (top.isEmpty()) {
            host.toast("اول از تنظیمات تگ‌ها چند ژانر محبوب انتخاب کن.")
            return
        }
        AlertDialog.Builder(host)
            .setTitle("این بار چه ژانری پیشنهاد بدهم؟")
            .setItems(top.toTypedArray()) { _, which ->
                if (which !in top.indices) return@setItems
                host.temporaryRecommendationGenre = top[which]
                host.sessionRejectedRecommendationIds.clear()
                host.recommendationGenreDialogShown = true
                host.showRecommendation()
            }
            .setNegativeButton("لغو", null)
            .create().also { it.show(); host.styleDialogWindow(it) }
    }

    private fun topGenres(): List<String> {
        val values = ArrayList(host.favoriteGenrePreferences)
        if (values.isEmpty()) {
            val all = host.allGenres()
            for (index in 0 until all.length()) values.add(all.optString(index))
        }
        values.sortWith(compareByDescending { host.recommendationPreferences.weightFor(it) })
        val top = ArrayList<String>(3)
        for (value in values) if (!host.containsIgnoreCase(top, value) && top.size < 3) top.add(value)
        if (top.size < 3) {
            val all = host.allGenres()
            for (index in 0 until all.length()) {
                val value = all.optString(index)
                if (!host.containsIgnoreCase(top, value) && top.size < 3) top.add(value)
            }
        }
        return top
    }

    fun showSettings() {
        host.setPage("recommendationSettings")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(host.makeHeader(
            "وزن‌های پیشنهاد", "", "menu", "help", Runnable { host.openDrawer() },
            Runnable { host.showRecommendationWeightsHelp() }, 0, 68, true
        ))
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(14f), host.dp(8f), host.dp(14f), host.dp(12f))
        }
        val detailsButton = host.text(
            if (host.recommendationDetailsVisible) "پنهان کردن جزئیات" else "نمایش جزئیات شاخص‌ها",
            13, host.TEXT
        ).apply {
            gravity = Gravity.CENTER
            background = host.roundDrawable(host.SURFACE, 0xFF40384A.toInt(), 12)
        }
        body.addView(detailsButton, LinearLayout.LayoutParams(-1, host.dp(44f)))

        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addView(rows)
        }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val books = host.recommendationBooks(null, emptySet())
        val breakdowns = HashMap<Long, BookRecommendationEngine.WeightBreakdown>()
        var maxTotal = 0.0
        var maxGenre = 0.0
        var maxAuthor = 0.0
        var maxRating = 0.0
        for (book in books) {
            val parts = host.recommendationEngine.weightBreakdown(book)
            breakdowns[book.id] = parts
            maxTotal = maxOf(maxTotal, parts.total)
            maxGenre = maxOf(maxGenre, parts.genre)
            maxAuthor = maxOf(maxAuthor, parts.author)
            maxRating = maxOf(maxRating, parts.rating)
        }
        val sortedBooks = books.sortedByDescending { breakdowns[it.id]?.total ?: 0.0 }
        addColorControls(rows, host.recommendationDetailsVisible)
        if (sortedBooks.isEmpty()) {
            rows.addView(host.emptyLabel("کتاب ناتمام برای بررسی وجود ندارد"),
                LinearLayout.LayoutParams(-1, host.dp(90f)))
        } else for (book in sortedBooks) {
            val parts = breakdowns[book.id] ?: continue
            addWeightCard(rows, book, parts, maxTotal, maxGenre, maxAuthor, maxRating,
                host.recommendationDetailsVisible)
        }
        detailsButton.setOnClickListener {
            host.recommendationDetailsVisible = !host.recommendationDetailsVisible
            host.showRecommendationSettings()
        }
        host.applyPressScale(detailsButton)
        host.appColumn.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun showWeightsHelp() {
        val help = host.text(
            "کتاب‌های ناتمام بر اساس وزن نهایی از بالا به پایین مرتب شده‌اند. وزن هر کتاب از ژانرهای آن، ترجیح نویسنده و امتیاز دستی همان کتاب ساخته می‌شود. امتیاز ستاره‌ای یک کتاب فقط وزن همان کتاب را تغییر می‌دهد.",
            14, host.TEXT
        ).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        AlertDialog.Builder(host).setTitle("راهنمای وزن‌ها")
            .setView(host.dialogPadding(help)).setPositiveButton("بستن", null).create()
            .also { it.show(); host.styleDialogWindow(it) }
    }

    private fun addColorControls(parent: LinearLayout, visible: Boolean) {
        if (!visible) return
        val keys = arrayOf("genre", "author", "rating")
        val labels = arrayOf("ژانر", "نویسنده", "امتیاز")
        val row = LinearLayout(host).apply { gravity = Gravity.CENTER }
        keys.forEachIndexed { index, key ->
            val chip = host.text(labels[index], 11, host.contrastOnAccent()).apply {
                gravity = Gravity.CENTER
                setPadding(host.dp(7f), 0, host.dp(7f), 0)
                background = host.roundDrawable(metricColor(key), Color.TRANSPARENT, 12)
            }
            val params = LinearLayout.LayoutParams(0, host.dp(32f), 1f).apply {
                leftMargin = host.dp(3f)
                rightMargin = host.dp(3f)
            }
            row.addView(chip, params)
            chip.setOnClickListener {
                val next = (metricColorIndex(key) + 1) % MainActivity.RECOMMENDATION_METRIC_COLORS.size
                host.recommendationState.edit().putInt("metric_color_$key", next).apply()
                host.showRecommendationSettings()
            }
            host.applyPressScale(chip, .98f)
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, host.dp(38f)).apply {
            bottomMargin = host.dp(5f)
        })
    }

    private fun metricColorIndex(key: String): Int {
        val defaultIndex = when (key) { "genre" -> 0; "author" -> 1; else -> 2 }
        return host.recommendationState.getInt("metric_color_$key", defaultIndex)
            .coerceIn(0, MainActivity.RECOMMENDATION_METRIC_COLORS.lastIndex)
    }

    private fun metricColor(key: String): Int = MainActivity.RECOMMENDATION_METRIC_COLORS[metricColorIndex(key)]

    private fun addWeightCard(
        parent: LinearLayout,
        book: BookRecommendationEngine.Book,
        parts: BookRecommendationEngine.WeightBreakdown,
        maxTotal: Double,
        maxGenre: Double,
        maxAuthor: Double,
        maxRating: Double,
        showDetails: Boolean,
    ) {
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12f), host.dp(9f), host.dp(12f), host.dp(9f))
            background = host.roundDrawable(host.SURFACE, 0xFF40384A.toInt(), 14)
        }
        val title = host.text(book.title, 14, host.TEXT).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val score = host.text(String.format(java.util.Locale.US, "%.2f", parts.total), 12, host.MUTED)
        val heading = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, host.dp(30f), 1f))
            addView(score, LinearLayout.LayoutParams(-2, host.dp(30f)))
        }
        card.addView(heading)
        addWeightTube(card, parts.total, maxTotal, host.PURPLE, 8)
        if (showDetails) {
            addWeightFactor(card, "ژانر", parts.genre, maxGenre, metricColor("genre"))
            addWeightFactor(card, "نویسنده", parts.author, maxAuthor, metricColor("author"))
            addWeightFactor(card, "امتیاز", parts.rating, maxRating, metricColor("rating"))
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = host.dp(8f) })
    }

    private fun addWeightFactor(card: LinearLayout, label: String, value: Double, max: Double, color: Int) {
        val row = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(host.text(label, 10, host.MUTED), LinearLayout.LayoutParams(host.dp(42f), host.dp(18f)))
        val tube = FrameLayout(host).apply { background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 8) }
        val fill = View(host).apply { background = host.roundDrawable(color, Color.TRANSPARENT, 8) }
        tube.addView(fill, FrameLayout.LayoutParams(0, -1, Gravity.LEFT))
        val amount = host.text(String.format(java.util.Locale.US, "%.2f", value), 10, host.MUTED)
        row.addView(tube, LinearLayout.LayoutParams(0, host.dp(8f), 1f).apply {
            leftMargin = host.dp(5f)
            rightMargin = host.dp(5f)
        })
        row.addView(amount, LinearLayout.LayoutParams(host.dp(38f), host.dp(18f)))
        tube.post {
            val params = fill.layoutParams as FrameLayout.LayoutParams
            params.width = if (max <= 0) 0 else (tube.width * value / max).roundToInt().coerceIn(0, tube.width)
            fill.layoutParams = params
        }
        card.addView(row)
    }

    private fun addWeightTube(card: LinearLayout, value: Double, max: Double, color: Int, heightDp: Int) {
        val tube = FrameLayout(host).apply { background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 8) }
        val fill = View(host).apply { background = host.roundDrawable(color, Color.TRANSPARENT, 8) }
        tube.addView(fill, FrameLayout.LayoutParams(0, -1, Gravity.LEFT))
        tube.post {
            val params = fill.layoutParams as FrameLayout.LayoutParams
            params.width = if (max <= 0) 0 else maxOf(
                host.dp(3f), minOf(tube.width, (tube.width * value / max).roundToInt())
            )
            fill.layoutParams = params
        }
        card.addView(tube, LinearLayout.LayoutParams(-1, host.dp(heightDp.toFloat())).apply {
            topMargin = host.dp(2f)
        })
    }

    fun showDescription(value: String) {
        val description = host.text(value, 15, host.TEXT).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
            setPadding(host.dp(14f), host.dp(12f), host.dp(14f), host.dp(12f))
        }
        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addView(description, ScrollView.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(host).setTitle("توضیحات کتاب")
            .setView(host.dialogPadding(scroll)).setPositiveButton("بستن", null).create()
            .also { it.show(); host.styleDialogWindow(it) }
    }
}
