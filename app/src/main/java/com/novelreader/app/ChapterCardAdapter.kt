package com.novelreader.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Builds chapter rows and their progress indicators for the chapter list. */
internal class ChapterCardAdapter(private val host: MainActivity) : BaseAdapter() {
    private fun chapters(): JSONArray? = host.activeBook()?.optJSONArray("chapters")

    override fun getCount(): Int = chapters()?.length() ?: 0
    override fun getItem(position: Int): Any? = chapters()?.optJSONObject(position)
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val chapter = chapters()?.optJSONObject(position) ?: ChapterCatalog.newChapter()
        val key = host.chapterSelectionKey(chapter, position)
        val selecting = host.selectedChapterKeys.isNotEmpty()
        val selected = host.selectedChapterKeys.contains(key)
        val hasText = chapter.optBoolean("hasText")
        val finishedWriting = hasText && chapter.optBoolean("end")
        val done = finishedWriting && chapter.optBoolean("done")

        val row = LinearLayout(host)
        row.orientation = LinearLayout.VERTICAL
        row.layoutDirection = View.LAYOUT_DIRECTION_LTR
        row.setPadding(host.dp(20f), host.dp(16f), host.dp(20f), host.dp(16f))
        row.tag = key
        row.background = host.roundDrawable(
            if (selected) (if (host.lightTheme) 0xFFEDE3FA.toInt() else 0xFF241B2C.toInt()) else host.SURFACE,
            if (selected) host.PURPLE else 0xFF29242F.toInt(),
            16
        )
        val rowParams = LinearLayout.LayoutParams(-1, -2)
        rowParams.bottomMargin = host.dp(7f)
        row.layoutParams = rowParams

        val heading = LinearLayout(host)
        heading.gravity = Gravity.CENTER_VERTICAL
        heading.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val leading = LinearLayout(host)
        leading.gravity = Gravity.CENTER_VERTICAL
        leading.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val name = host.text(ChapterCatalog.label(chapter, position), 16, host.TEXT)
        name.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        name.isSingleLine = true
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        val estimate = host.text(host.remainingChapterTimeLabel(chapter), 14, host.MUTED)
        estimate.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val iconName = when {
            selecting -> if (selected) "reorder" else "select"
            done -> "check"
            hasText -> "edit"
            else -> "book"
        }
        val iconColor = when {
            selecting -> if (selected) host.PURPLE else host.MUTED
            done -> host.GREEN
            hasText -> host.BLUE
            else -> host.PURPLE
        }
        val status = when {
            selecting -> host.plainIconButton(iconName, iconColor) { host.toggleChapterSelection(chapter, position) }
            finishedWriting && !done -> host.pairedEditCheckedButton(host.PURPLE, null)
            else -> host.plainIconButton(iconName, iconColor, null)
        }
        if (selecting && selected) {
            status.setOnLongClickListener { host.startReorderDrag(row, "chapter", key) }
        }
        val statusParams = LinearLayout.LayoutParams(host.dp(30f), host.dp(30f))
        statusParams.rightMargin = host.dp(12f)
        leading.addView(status, statusParams)
        leading.addView(name, LinearLayout.LayoutParams(0, host.dp(26f), 1f))
        heading.addView(leading, LinearLayout.LayoutParams(0, host.dp(26f), 1f))
        val estimateParams = LinearLayout.LayoutParams(-2, host.dp(26f))
        estimateParams.leftMargin = host.dp(10f)
        heading.addView(estimate, estimateParams)
        row.addView(heading, LinearLayout.LayoutParams(-1, host.dp(26f)))

        val meter = FrameLayout(host)
        meter.background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 3)
        val fill = View(host)
        val progressGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFFF1C40F.toInt(), 0xFFE74C3C.toInt())
        )
        progressGradient.cornerRadius = host.dp(2f).toFloat()
        fill.background = progressGradient
        val fillPercent = if (!finishedWriting) 0 else if (done) 100 else max(0, min(100, chapter.optInt("percent")))
        meter.addView(fill, FrameLayout.LayoutParams(0, host.dp(3f), Gravity.LEFT))
        meter.post {
            val params = fill.layoutParams as FrameLayout.LayoutParams
            params.width = (meter.width * fillPercent / 100f).roundToInt()
            fill.layoutParams = params
        }
        val meterParams = LinearLayout.LayoutParams(-1, host.dp(3f))
        meterParams.topMargin = host.dp(12f)
        row.addView(meter, meterParams)

        row.isClickable = true
        row.setOnClickListener {
            if (host.selectedChapterKeys.isNotEmpty()) host.toggleChapterSelection(chapter, position)
            else host.openReader(position)
        }
        row.setOnLongClickListener {
            if (host.selectedChapterKeys.contains(key)) host.startReorderDrag(row, "chapter", key)
            else {
                host.toggleChapterSelection(chapter, position)
                true
            }
        }
        host.setReorderDropTarget(row, "chapter", key)
        host.applyPressScale(row, .96f)
        return row
    }
}
