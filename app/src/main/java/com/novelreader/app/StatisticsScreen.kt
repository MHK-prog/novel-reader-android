package com.novelreader.app

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.TreeSet

/** Builds the reading statistics page while MainActivity owns navigation and stored data. */
internal class StatisticsScreen(private val host: MainActivity) {
    private var range = ReadingStatsStore.WEEK
    private var periodOffset = 0
    private var detailsVisible = false

    fun show() {
        host.setPage("statistics")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(host.makeHeader("آمار مطالعه", "", "menu", null, Runnable { host.openDrawer() }, null))

        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(16f), host.dp(12f), host.dp(16f), host.dp(18f))
        }

        val ranges = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val labels = arrayOf("روز", "هفته", "ماه", "سال")
        labels.forEachIndexed { index, label ->
            val params = LinearLayout.LayoutParams(0, host.dp(40f), 1f)
            if (index > 0) params.leftMargin = host.dp(5f)
            ranges.addView(statisticsChoice(label, range == index) {
                range = index
                periodOffset = 0
                show()
            }, params)
        }
        body.addView(ranges, LinearLayout.LayoutParams(-1, -2).apply { topMargin = host.dp(14f) })

        val now = System.currentTimeMillis()
        val points = host.readingStats?.points(range, now, periodOffset).orEmpty()
        val total = points.sumOf { it.value }
        val summary = host.text(host.formatReadingTime(total), 24, host.PURPLE).apply {
            gravity = Gravity.CENTER
        }
        val summaryLabel = host.text("زمان مطالعه در این بازه", 13, host.MUTED).apply {
            gravity = Gravity.CENTER
        }
        val summaryCard = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(host.dp(12f), host.dp(12f), host.dp(12f), host.dp(12f))
            background = host.roundDrawable(host.SURFACE, 0xFF302A38.toInt(), 13)
            addView(summary)
            addView(summaryLabel)
        }
        body.addView(summaryCard, LinearLayout.LayoutParams(-1, host.dp(82f)).apply {
            topMargin = host.dp(14f)
        })

        val chartTools = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        chartTools.addView(host.iconButton("back", host.PURPLE, false) {
            periodOffset++
            show()
        }, LinearLayout.LayoutParams(host.dp(42f), host.dp(42f)))
        val periodLabel = host.text(
            host.readingStats?.periodLabel(range, now, periodOffset).orEmpty(), 13, host.TEXT
        ).apply { gravity = Gravity.CENTER }
        chartTools.addView(periodLabel, LinearLayout.LayoutParams(0, host.dp(42f), 1f))
        chartTools.addView(host.iconButton("next", host.PURPLE, false) {
            if (periodOffset > 0) periodOffset--
            show()
        }, LinearLayout.LayoutParams(host.dp(42f), host.dp(42f)))
        body.addView(chartTools, LinearLayout.LayoutParams(-1, host.dp(44f)).apply {
            topMargin = host.dp(8f)
        })

        val detailToggle = host.textButton(
            if (detailsVisible) "پنهان کردن جزئیات" else "نمایش جزئیات", "chart", host.PURPLE
        ) {
            detailsVisible = !detailsVisible
            show()
        }
        body.addView(detailToggle, LinearLayout.LayoutParams(-1, host.dp(42f)).apply {
            topMargin = host.dp(4f)
        })

        val chart = ReadingStatsChartView(host, host.PURPLE, host.MUTED, host.vazir)
        val bookLabels = HashMap<String, String>()
        val books = host.library.optJSONArray("books")
        if (books != null) for (index in 0 until books.length()) {
            val book = books.optJSONObject(index) ?: continue
            bookLabels[book.optString("id")] = host.displayBookTitle(book)
        }
        chart.setData(
            points, host.PURPLE, host.MUTED, host.vazir, detailsVisible,
            range == ReadingStatsStore.DAY || range == ReadingStatsStore.WEEK
        )
        val chartSection = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        chartSection.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = host.dp(8f)
        })
        if (detailsVisible) addLegend(chartSection, points, bookLabels, chart)
        body.addView(chartSection, LinearLayout.LayoutParams(-1, 0, 1f))
        host.appColumn.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun addLegend(
        chartSection: LinearLayout,
        points: List<ReadingStatsStore.Point>,
        bookLabels: Map<String, String>,
        chart: ReadingStatsChartView,
    ) {
        val legendScroll = HorizontalScrollView(host).apply {
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
        }
        val legend = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(4f), host.dp(3f), host.dp(4f), host.dp(3f))
        }
        val legendIds = TreeSet<String>()
        points.forEach { legendIds.addAll(it.byBook.keys) }
        legendIds.forEach { id ->
            val item = LinearLayout(host).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(host.dp(7f), 0, host.dp(7f), 0)
            }
            val swatch = View(host).apply {
                background = host.roundDrawable(chart.colorForBook(id), Color.TRANSPARENT, 5)
            }
            item.addView(swatch, LinearLayout.LayoutParams(host.dp(12f), host.dp(12f)))
            val label = bookLabels[id] ?: "کتاب حذف‌شده"
            val name = host.text(label, 11, host.MUTED)
            item.addView(name, LinearLayout.LayoutParams(-2, host.dp(24f)).apply {
                leftMargin = host.dp(5f)
            })
            legend.addView(item, LinearLayout.LayoutParams(-2, host.dp(30f)))
        }
        legendScroll.addView(legend)
        chartSection.addView(legendScroll, LinearLayout.LayoutParams(-1, host.dp(36f)))
    }

    private fun statisticsChoice(label: String, selected: Boolean, action: () -> Unit): TextView =
        host.text(label, 13, if (selected) host.contrastOnAccent() else host.TEXT).apply {
            gravity = Gravity.CENTER
            background = host.roundDrawable(
                if (selected) host.PURPLE else host.SURFACE,
                if (selected) host.PURPLE else 0xFF40384A.toInt(),
                12
            )
            setOnClickListener { action() }
            host.applyPressScale(this)
        }
}
