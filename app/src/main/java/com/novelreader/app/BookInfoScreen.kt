package com.novelreader.app

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Owns book details and its local display controls. */
internal class BookInfoScreen(private val host: MainActivity) {
    fun show(book: JSONObject?) {
        if (book == null) {
            host.showDashboard()
            return
        }
        host.setPage("bookInfo")
        host.activeBookId = book.optString("id")
        host.activeCollectionId = book.optString("collectionId", "other")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(
            host.makeHeader(
                host.displayBookTitle(book), book.optString("author"),
                "menu", "edit", Runnable { host.openDrawer() }, Runnable { host.showBookDialog(book) }, 10
            ),
            LinearLayout.LayoutParams(-1, host.dp(62f))
        )
        host.titleView?.apply {
            contentDescription = "کپی نام کتاب و نویسنده"
            setOnClickListener { host.copyBookCredits(book) }
        }
        host.subtitleView?.apply {
            contentDescription = "کپی نام کتاب و نویسنده"
            setOnClickListener { host.copyBookCredits(book) }
        }

        val scroll = ScrollView(host)
        scroll.isVerticalScrollBarEnabled = false
        scroll.isHorizontalScrollBarEnabled = false
        val body = LinearLayout(host)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(host.dp(18f), host.dp(14f), host.dp(18f), host.dp(22f))

        val coverFrame = FrameLayout(host)
        coverFrame.background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 12)
        coverFrame.clipToOutline = true
        val cover = ImageView(host)
        cover.scaleType = ImageView.ScaleType.FIT_CENTER
        cover.background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 12)
        coverFrame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        val coverPlaceholder = NativeIconView(host, "book", host.PURPLE)
        coverFrame.addView(
            coverPlaceholder,
            FrameLayout.LayoutParams(host.dp(48f), host.dp(48f), Gravity.CENTER)
        )
        host.bindBookCover(cover, coverPlaceholder, book)
        val coverParams = LinearLayout.LayoutParams(host.dp(166f), host.dp(230f))
        coverParams.gravity = Gravity.CENTER_HORIZONTAL
        coverParams.bottomMargin = host.dp(18f)
        body.addView(coverFrame, coverParams)
        addCoverFilterSwitch(body, book, cover, coverPlaceholder)

        val chapters = book.optJSONArray("chapters")
        val chapterCount = chapters?.length() ?: 0
        val readPercent = host.bookReadPercent(book)
        body.addView(createGenreSummary(book), host.fieldParams())
        addSeriesNumberLine(body, book)
        host.addBookInfoLine(body, "تعداد Chapter", chapterCount.toString())
        host.addBookInfoLine(body, "میزان مطالعه", "$readPercent٪")

        val progress = ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.progress = readPercent
        progress.progressDrawable = host.createSplashProgressDrawable()
        val progressParams = LinearLayout.LayoutParams(-1, host.dp(5f))
        progressParams.bottomMargin = host.dp(16f)
        body.addView(progress, progressParams)

        val descriptionValue = book.optString("description", "").trim()
        val descriptionLabel = host.text("توضیحات", 14, host.MUTED)
        descriptionLabel.gravity = Gravity.RIGHT
        body.addView(descriptionLabel)
        val description = host.text(
            descriptionValue.ifEmpty { "توضیحی ثبت نشده" },
            15,
            if (descriptionValue.isEmpty()) host.MUTED else host.TEXT
        )
        description.gravity = Gravity.RIGHT
        description.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        description.setPadding(0, host.dp(6f), 0, host.dp(18f))
        body.addView(description, LinearLayout.LayoutParams(-1, -2))

        body.addView(createBookRatingControl(book), LinearLayout.LayoutParams(-1, -2))
        val chaptersButton = host.textButton(
            "رفتن به فهرست Chapters", "book", host.PURPLE
        ) { host.openChapters(book) }
        val chaptersButtonParams = LinearLayout.LayoutParams(-1, host.dp(50f))
        chaptersButtonParams.topMargin = host.dp(18f)
        body.addView(chaptersButton, chaptersButtonParams)

        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun createBookRatingControl(book: JSONObject): LinearLayout {
        val card = LinearLayout(host)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.setPadding(host.dp(12f), host.dp(12f), host.dp(12f), host.dp(12f))
        card.background = host.roundDrawable(host.SURFACE, 0xFF29242F.toInt(), 11)
        val title = host.text("علاقهٔ من به این کتاب", 14, host.TEXT)
        title.gravity = Gravity.CENTER
        card.addView(title)
        val ratingLabel = host.text("", 12, host.MUTED)
        ratingLabel.gravity = Gravity.CENTER
        val labelParams = LinearLayout.LayoutParams(-1, -2)
        labelParams.topMargin = host.dp(3f)
        card.addView(ratingLabel, labelParams)
        val stars = LinearLayout(host)
        stars.gravity = Gravity.CENTER
        stars.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val starViews = mutableListOf<View>()
        for (i in 1..5) {
            val value = i
            val star = ratingStar(book.optInt("userRating", 0).coerceIn(0, 5) >= i, i)
            star.isClickable = true
            star.isFocusable = true
            star.setOnClickListener { setManualBookRating(book, value, ratingLabel, starViews) }
            host.applyPressScale(star, .93f)
            starViews.add(star)
            stars.addView(star, LinearLayout.LayoutParams(host.dp(42f), host.dp(46f)))
        }
        updateBookRatingLabel(ratingLabel, book.optInt("userRating", 0))
        val starsParams = LinearLayout.LayoutParams(-1, -2)
        starsParams.topMargin = host.dp(4f)
        card.addView(stars, starsParams)
        return card
    }

    fun showGenres(book: JSONObject?) {
        val genres = book?.optJSONArray("genres")
        val items = LinearLayout(host)
        items.orientation = LinearLayout.VERTICAL
        items.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val accordion = AccordionController()
        if (genres != null) for (i in 0 until genres.length()) {
            val genre = genres.optString(i).trim()
            if (genre.isEmpty()) continue
            val detail = host.findGenreDetail(genre)
            val description = detail?.optString("description", "")?.trim().orEmpty()
            val item = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(host.dp(12f), host.dp(10f), host.dp(12f), host.dp(10f))
                background = host.roundDrawable(host.SURFACE_ALT, host.themedStrokeColor(0xFF40384A.toInt()), 12)
            }
            val heading = LinearLayout(host).apply {
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            val name = host.text(genre, 15, host.TEXT).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
            val chevron = NativeIconView(host, "chevron_down", host.PURPLE)
            heading.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(chevron, LinearLayout.LayoutParams(host.dp(20f), host.dp(20f)))
            item.addView(heading)
            val note = host.text(description.ifEmpty { "توضیحی برای این ژانر ثبت نشده" }, 13,
                if (description.isEmpty()) host.MUTED else host.TEXT).apply {
                gravity = Gravity.RIGHT
                setLineSpacing(host.dp(2f).toFloat(), 1.25f)
                setPadding(0, host.dp(8f), 0, host.dp(2f))
                visibility = View.GONE
            }
            item.addView(note, LinearLayout.LayoutParams(-1, -2))
            item.isClickable = true
            item.isFocusable = true
            item.setOnClickListener { accordion.toggle(note, chevron) }
            host.applyPressScale(item, .985f)
            val itemParams = LinearLayout.LayoutParams(-1, -2)
            itemParams.bottomMargin = host.dp(8f)
            items.addView(item, itemParams)
        }
        if (items.childCount == 0) {
            val empty = host.text("ژانری برای این کتاب ثبت نشده", 14, host.MUTED)
            empty.gravity = Gravity.CENTER
            items.addView(empty, LinearLayout.LayoutParams(-1, host.dp(64f)))
        }
        val scroll = ScrollView(host)
        scroll.isFillViewport = false
        scroll.isVerticalScrollBarEnabled = false
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(items, ViewGroup.LayoutParams(-1, -2))
        val padded = LinearLayout(host)
        padded.gravity = Gravity.CENTER_HORIZONTAL
        padded.setPadding(host.dp(20f), host.dp(8f), host.dp(20f), host.dp(8f))
        val maxHeight = min(host.dp(460f), (host.resources.displayMetrics.heightPixels * .58f).toInt())
        padded.addView(scroll, LinearLayout.LayoutParams(-1, maxHeight))
        val dialog = AlertDialog.Builder(host)
            .setTitle("ژانرهای کتاب")
            .setView(padded)
            .setPositiveButton("بستن", null)
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    private fun createGenreSummary(book: JSONObject): LinearLayout {
        val card = LinearLayout(host)
        card.orientation = LinearLayout.VERTICAL
        card.layoutDirection = View.LAYOUT_DIRECTION_RTL
        card.setPadding(host.dp(14f), host.dp(10f), host.dp(14f), host.dp(10f))
        card.background = host.roundDrawable(host.SURFACE, 0xFF29242F.toInt(), 12)
        val heading = LinearLayout(host)
        heading.layoutDirection = View.LAYOUT_DIRECTION_RTL
        heading.gravity = Gravity.CENTER_VERTICAL
        val label = host.text("ژانرها", 13, host.MUTED)
        label.isSingleLine = true
        label.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        heading.addView(label, LinearLayout.LayoutParams(0, host.dp(22f), 1f))
        val hint = host.text("برای مشاهده بزن", 11, host.PURPLE)
        hint.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        heading.addView(hint, LinearLayout.LayoutParams(-2, host.dp(22f)))
        card.addView(heading)
        card.contentDescription = "نمایش همهٔ ژانرهای کتاب"
        card.setOnClickListener { showGenres(book) }
        host.applyPressScale(card, .98f)
        return card
    }

    private fun addSeriesNumberLine(body: LinearLayout, book: JSONObject) {
        val numbered = book.optString("collectionId", "other") != "other"
        val row = LinearLayout(host)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(host.dp(12f), host.dp(10f), host.dp(12f), host.dp(10f))
        row.background = host.roundDrawable(host.SURFACE, 0xFF29242F.toInt(), 11)
        val label = host.text("شمارهٔ کتاب در مجموعه", 13, host.MUTED)
        val value = host.text(if (numbered) host.seriesBookNumber(book).toString() else "—", 14, host.TEXT)
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(value, LinearLayout.LayoutParams(-2, -2))
        val params = LinearLayout.LayoutParams(-1, host.dp(48f))
        params.bottomMargin = host.dp(5f)
        body.addView(row, params)
    }

    private fun addCoverFilterSwitch(body: LinearLayout, book: JSONObject, cover: ImageView, placeholder: View) {
        val row = LinearLayout(host)
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        row.setPadding(host.dp(14f), 0, host.dp(12f), 0)
        row.background = host.roundDrawable(host.SURFACE, 0xFF302A38.toInt(), 12)
        val label = host.text("پوشاندن جلد در فهرست‌ها", 14, host.TEXT)
        row.addView(label, LinearLayout.LayoutParams(0, -1, 1f))
        val toggle = Switch(host)
        toggle.isChecked = book.optBoolean("coverFiltered", false)
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            toggle.thumbTintList = ColorStateList(states, intArrayOf(host.PURPLE, host.MUTED))
            toggle.trackTintList = ColorStateList(states, intArrayOf(
                Color.argb(100, Color.red(host.PURPLE), Color.green(host.PURPLE), Color.blue(host.PURPLE)),
                0xFF625D66.toInt()
            ))
        }
        lateinit var listener: CompoundButton.OnCheckedChangeListener
        listener = CompoundButton.OnCheckedChangeListener { button, checked ->
            val previous = !checked
            host.putJson(book, "coverFiltered", checked)
            if (!host.saveLibrary()) {
                host.putJson(book, "coverFiltered", previous)
                button.setOnCheckedChangeListener(null)
                button.isChecked = previous
                button.setOnCheckedChangeListener(listener)
                return@OnCheckedChangeListener
            }
            host.bindBookCover(cover, placeholder, book)
        }
        toggle.setOnCheckedChangeListener(listener)
        row.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        val params = LinearLayout.LayoutParams(-1, host.dp(52f))
        params.bottomMargin = host.dp(14f)
        body.addView(row, params)
    }

    private fun updateBookRatingLabel(label: TextView, rating: Int) {
        label.text = if (rating <= 0) "هنوز امتیازی ثبت نشده" else "امتیاز من: $rating از 5"
    }

    private fun setManualBookRating(book: JSONObject, rating: Int, label: TextView, stars: List<View>) {
        val previous = book.optInt("userRating", 0)
        host.putJson(book, "userRating", rating)
        if (!host.saveLibrary()) host.putJson(book, "userRating", previous)
        val savedRating = book.optInt("userRating", 0).coerceIn(0, 5)
        updateBookRatingLabel(label, savedRating)
        host.rebuildRecommendationWeights()
        host.persistRecommendationState()
        for (i in stars.indices) {
            stars[i].isSelected = i < savedRating
            stars[i].invalidate()
        }
    }

    private fun ratingStar(selected: Boolean, rating: Int): View {
        val star = object : View(host) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val outer = min(width, height) * .39f
                val inner = outer * .48f
                val centerX = width / 2f
                val centerY = height / 2f
                val xs = FloatArray(10)
                val ys = FloatArray(10)
                for (i in 0 until 10) {
                    val angle = -Math.PI / 2 + i * Math.PI / 5
                    val radius = if (i % 2 == 0) outer else inner
                    xs[i] = centerX + kotlin.math.cos(angle).toFloat() * radius
                    ys[i] = centerY + kotlin.math.sin(angle).toFloat() * radius
                }
                val path = Path()
                path.moveTo(xs[0] + (xs[9] - xs[0]) * .18f, ys[0] + (ys[9] - ys[0]) * .18f)
                for (i in 0 until 10) {
                    val next = (i + 1) % 10
                    val previous = (i + 9) % 10
                    val beforeX = xs[i] + (xs[previous] - xs[i]) * .18f
                    val beforeY = ys[i] + (ys[previous] - ys[i]) * .18f
                    val afterX = xs[i] + (xs[next] - xs[i]) * .18f
                    val afterY = ys[i] + (ys[next] - ys[i]) * .18f
                    path.lineTo(beforeX, beforeY)
                    path.quadTo(xs[i], ys[i], afterX, afterY)
                }
                path.close()
                val filled = isSelected
                paint.color = if (filled) Color.rgb(255, 193, 7) else host.MUTED
                paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
                paint.strokeWidth = host.dp(1.8f).toFloat()
                paint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(path, paint)
            }
        }
        star.isSelected = selected
        star.contentDescription = "$rating از 5"
        star.isClickable = true
        star.isFocusable = true
        return star
    }
}
