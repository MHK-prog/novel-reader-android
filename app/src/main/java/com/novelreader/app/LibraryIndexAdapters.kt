package com.novelreader.app

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

/** Kotlin adapters for the author, genre, and collection index screens. */
internal class AuthorCardAdapter(private val host: MainActivity) : BaseAdapter() {
    private var cachedAuthors: List<String>? = null

    override fun notifyDataSetChanged() {
        cachedAuthors = null
        super.notifyDataSetChanged()
    }

    private fun visible(): List<String> {
        cachedAuthors?.let { return it }
        val unique = mutableListOf<String>()
        val details = host.authorDetails()
        for (i in 0 until details.length()) {
            val item = details.optJSONObject(i) ?: continue
            addUniqueIgnoreCase(unique, item.optString("name", "").trim())
        }
        host.favoriteAuthorPreferences.forEach { addUniqueIgnoreCase(unique, it) }
        host.dislikedAuthorPreferences.forEach { addUniqueIgnoreCase(unique, it) }
        val query = host.authorSearchField?.text?.toString().orEmpty().trim().lowercase(Locale.ROOT)
        return unique.filter { it.lowercase(Locale.ROOT).contains(query) }.also { cachedAuthors = it }
    }

    private fun addUniqueIgnoreCase(values: MutableList<String>, value: String) {
        if (value.isBlank() || values.any { it.equals(value, ignoreCase = true) }) return
        values.add(value)
    }

    fun getAuthor(position: Int): String = visible()[position]
    override fun getCount(): Int = visible().size
    override fun getItem(position: Int): Any = getAuthor(position)
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val author = getAuthor(position)
        val selected = host.containsIgnoreCase(host.selectedAuthorNames, author)
        val frame = FrameLayout(host)
        frame.isClickable = true
        frame.isFocusable = true
        frame.contentDescription = "$author، نمایش کتاب‌ها"
        frame.tag = author
        frame.setOnClickListener {
            if (host.selectedAuthorNames.isNotEmpty()) host.toggleAuthorSelection(author)
            else host.selectAuthorCard(author)
        }
        frame.setOnLongClickListener {
            if (host.containsIgnoreCase(host.selectedAuthorNames, author)) {
                host.startReorderDrag(frame, "author", author)
            } else {
                host.toggleAuthorSelection(author)
                true
            }
        }
        host.setReorderDropTarget(frame, "author", author)

        val card = LinearLayout(host)
        card.gravity = Gravity.CENTER_VERTICAL
        card.layoutDirection = View.LAYOUT_DIRECTION_RTL
        card.setPadding(host.dp(14f), host.dp(7f), host.dp(14f), host.dp(7f))
        card.background = host.roundDrawable(
            if (selected) (if (host.lightTheme) 0xFFEDE3FA.toInt() else 0xFF241B2C.toInt()) else host.SURFACE,
            if (selected) host.PURPLE else 0xFF29242F.toInt(),
            12
        )
        card.layoutParams = FrameLayout.LayoutParams(-1, -1)
        val icon = NativeIconView(host, if (selected) "reorder" else host.authorIcon(author), host.PURPLE)
        val iconParams = LinearLayout.LayoutParams(host.dp(30f), host.dp(30f))
        iconParams.leftMargin = host.dp(9f)
        card.addView(icon, iconParams)
        val title = host.text(author, 15, host.TEXT)
        title.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        title.isSingleLine = true
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        val count = host.text("${host.authorBookCount(author)} کتاب", 12, host.MUTED)
        count.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        card.addView(count, LinearLayout.LayoutParams(-2, -1))
        frame.addView(card, FrameLayout.LayoutParams(-1, -1))
        frame.layoutParams = AbsListView.LayoutParams(-1, host.dp(68f))
        host.applyPressScale(frame, .96f)
        return frame
    }
}

internal class GenreCardAdapter(private val host: MainActivity) : BaseAdapter() {
    private fun visible(): List<String> {
        val result = mutableListOf<String>()
        val details = host.genreDetails()
        for (i in 0 until details.length()) {
            val item = details.optJSONObject(i) ?: continue
            result.add(item.optString("name"))
        }
        return result
    }

    fun getGenre(position: Int): String = visible()[position]
    override fun getCount(): Int = visible().size
    override fun getItem(position: Int): Any = getGenre(position)
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val genre = getGenre(position)
        val selected = host.selectedGenreNames.contains(genre)
        val frame = FrameLayout(host)
        frame.tag = genre
        host.setReorderDropTarget(frame, "genre", genre)
        val card = LinearLayout(host)
        card.gravity = Gravity.CENTER_VERTICAL
        card.layoutDirection = View.LAYOUT_DIRECTION_RTL
        card.setPadding(host.dp(14f), host.dp(7f), host.dp(14f), host.dp(7f))
        card.background = host.roundDrawable(
            if (selected) (if (host.lightTheme) 0xFFEDE3FA.toInt() else 0xFF241B2C.toInt()) else host.SURFACE,
            if (selected) host.PURPLE else 0xFF29242F.toInt(),
            12
        )
        card.layoutParams = FrameLayout.LayoutParams(-1, -1)
        val title = host.text(genre, 15, host.TEXT)
        title.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        val icon = NativeIconView(host, if (selected) "reorder" else "tag", host.PURPLE)
        val iconParams = LinearLayout.LayoutParams(host.dp(30f), host.dp(30f))
        iconParams.leftMargin = host.dp(9f)
        card.addView(icon, iconParams)
        frame.addView(card, FrameLayout.LayoutParams(-1, -1))
        frame.layoutParams = AbsListView.LayoutParams(-1, host.dp(68f))
        host.applyPressScale(frame, .96f)
        return frame
    }
}

internal class CollectionCardAdapter(private val host: MainActivity) : BaseAdapter() {
    private var cachedCollections: List<JSONObject>? = null

    override fun notifyDataSetChanged() {
        cachedCollections = null
        super.notifyDataSetChanged()
    }

    private fun visible(): List<JSONObject> {
        cachedCollections?.let { return it }
        val result = mutableListOf<JSONObject>()
        val collections = host.collectionArray()
        for (i in 0 until collections.length()) collections.optJSONObject(i)?.let(result::add)
        return result.also { cachedCollections = it }
    }

    fun getCollection(position: Int): JSONObject = visible()[position]
    override fun getCount(): Int = visible().size
    override fun getItem(position: Int): Any = getCollection(position)
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val collection = getCollection(position)
        val id = collection.optString("id")
        val selected = host.selectedCollectionIds.contains(id)
        val card = LinearLayout(host)
        card.gravity = Gravity.CENTER_VERTICAL
        card.layoutDirection = View.LAYOUT_DIRECTION_RTL
        card.setPadding(host.dp(14f), host.dp(7f), host.dp(14f), host.dp(7f))
        card.background = host.roundDrawable(
            if (selected) (if (host.lightTheme) 0xFFEDE3FA.toInt() else 0xFF241B2C.toInt()) else host.SURFACE,
            if (selected) host.PURPLE else 0xFF29242F.toInt(),
            12
        )
        card.layoutParams = AbsListView.LayoutParams(-1, host.dp(68f))
        card.tag = id
        val icon = NativeIconView(host, if (selected) "reorder" else "folder", host.PURPLE)
        val iconParams = LinearLayout.LayoutParams(host.dp(30f), host.dp(30f))
        iconParams.leftMargin = host.dp(9f)
        card.addView(icon, iconParams)
        val title = host.text(collection.optString("title"), 15, host.TEXT)
        title.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        title.isSingleLine = true
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        val count = host.text("${host.collectionBookCount(id)} کتاب", 12, host.MUTED)
        count.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        card.addView(count, LinearLayout.LayoutParams(-2, -1))
        host.applyPressScale(card, .96f)
        host.setReorderDropTarget(card, "collection", id)
        return card
    }
}
