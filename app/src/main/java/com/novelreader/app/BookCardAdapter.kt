package com.novelreader.app

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.roundToInt

/** Renders the two-column book library independently from the activity navigation code. */
internal class BookCardAdapter(private val host: MainActivity) : BaseAdapter() {
    private var cachedBooks: List<JSONObject>? = null

    override fun notifyDataSetChanged() {
        cachedBooks = null
        super.notifyDataSetChanged()
    }

    fun visible(): List<JSONObject> {
        cachedBooks?.let { return it }
        val query = host.searchField?.text?.toString().orEmpty()
        val sort = host.getSharedPreferences("general", Activity.MODE_PRIVATE)
            .getString("book_sort", "recent") ?: "recent"
        return BookCatalog.visibleBooks(
            host.library,
            query,
            host.showingAllBooks(),
            host.activeCollectionId,
            host.activeGenreFilter,
            sort
        ).also { cachedBooks = it }
    }

    fun getBook(position: Int): JSONObject = visible()[position]
    override fun getCount(): Int = visible().size
    override fun getItem(position: Int): Any = getBook(position)
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val book = getBook(position)
        val id = book.optString("id")
        val selected = host.selectedBookIds.contains(id)
        val card = LinearLayout(host)
        card.orientation = LinearLayout.VERTICAL
        card.tag = id
        card.setPadding(host.dp(7f), host.dp(7f), host.dp(7f), host.dp(4f))
        card.background = host.roundDrawable(
            if (selected) (if (host.lightTheme) 0xFFEDE3FA.toInt() else 0xFF241B2C.toInt()) else host.SURFACE,
            if (selected) host.PURPLE else 0xFF29242F.toInt(),
            13
        )
        card.isClickable = true
        card.isFocusable = true
        host.applyPressScale(card, .96f)
        card.setOnClickListener { host.onBookCardTap(book) }
        card.setOnLongClickListener {
            if (host.selectedBookIds.contains(id)) host.startReorderDrag(card, "book", id)
            else {
                host.toggleBookSelection(book)
                true
            }
        }
        host.setReorderDropTarget(card, "book", id)

        val gridWidth = if (parent.width > 0) parent.width else host.resources.displayMetrics.widthPixels
        val cellWidth = maxOf(host.dp(120f), (gridWidth - host.dp(34f)) / 2)
        val artworkHeight = maxOf(host.dp(160f), ((cellWidth - host.dp(14f)) * 1.42f).roundToInt())
        card.layoutParams = AbsListView.LayoutParams(-1, artworkHeight + host.dp(53f))

        val artwork = FrameLayout(host)
        artwork.background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 9)
        artwork.clipToOutline = true
        val cover = ImageView(host)
        cover.scaleType = ImageView.ScaleType.FIT_CENTER
        cover.background = host.roundDrawable(host.SURFACE_ALT, Color.TRANSPARENT, 9)
        artwork.addView(cover, FrameLayout.LayoutParams(-1, -1))
        val placeholder = NativeIconView(host, "book", host.PURPLE)
        artwork.addView(placeholder, FrameLayout.LayoutParams(host.dp(42f), host.dp(42f), Gravity.CENTER))
        artwork.tag = placeholder
        host.bindBookCover(cover, placeholder, book)

        val selectionBadge = FrameLayout(host)
        selectionBadge.background = host.roundDrawable(
            if (selected) host.PURPLE else 0x99000000.toInt(),
            if (selected) host.PURPLE else Color.TRANSPARENT,
            20
        )
        val check = NativeIconView(host, if (selected) "reorder" else "select", Color.WHITE)
        selectionBadge.addView(check, FrameLayout.LayoutParams(host.dp(22f), host.dp(22f), Gravity.CENTER))
        val badgeParams = FrameLayout.LayoutParams(host.dp(32f), host.dp(32f), Gravity.TOP or Gravity.RIGHT)
        badgeParams.topMargin = host.dp(6f)
        badgeParams.rightMargin = host.dp(6f)
        artwork.addView(selectionBadge, badgeParams)
        selectionBadge.visibility = if (selected || host.selectedBookIds.isNotEmpty()) View.VISIBLE else View.GONE
        card.addView(artwork, LinearLayout.LayoutParams(-1, artworkHeight))

        val title = host.text(host.displayBookTitle(book), 14, host.TEXT)
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.gravity = Gravity.CENTER
        card.addView(title, LinearLayout.LayoutParams(-1, host.dp(42f)))
        return card
    }
}
