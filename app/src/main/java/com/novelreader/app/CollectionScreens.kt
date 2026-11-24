package com.novelreader.app

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.HashSet
import java.util.UUID

/** Owns the collection library, collection details, and collection dialogs. */
internal class CollectionScreens(private val host: MainActivity) {
    fun showCreateCollectionDialog() {
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val name = host.edit("نام مجموعه", false)
        content.addView(host.clearableInput(name), host.fieldParams())
        val chooseLabel = host.text("کتاب‌های این مجموعه را انتخاب کن", 13, host.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(10f), 0, host.dp(6f))
        }
        content.addView(chooseLabel)

        val bookScroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val choices = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        bookScroll.addView(choices)
        val selected = HashSet<String>()
        val available = mutableListOf<JSONObject>()
        val books = host.library.optJSONArray("books")
        if (books != null) {
            for (index in 0 until books.length()) {
                val book = books.optJSONObject(index) ?: continue
                val collectionId = book.optString("collectionId", "other")
                if (collectionId.isEmpty() || collectionId == "other" ||
                    host.findCollectionById(collectionId) == null
                ) {
                    available.add(book)
                }
            }
        }

        lateinit var renderChoices: () -> Unit
        renderChoices = {
            choices.removeAllViews()
            if (available.isEmpty()) {
                choices.addView(host.emptyLabel("کتابی در مجموعهٔ سایر نیست"),
                    LinearLayout.LayoutParams(-1, host.dp(72f)))
            } else {
                for (book in available) {
                    val id = book.optString("id")
                    val row = selectionBookRow(book.optString("title"), selected.contains(id), "add", 24)
                    choices.addView(row, LinearLayout.LayoutParams(-1, host.dp(48f)).apply {
                        bottomMargin = host.dp(6f)
                    })
                    row.setOnClickListener {
                        if (!selected.add(id)) selected.remove(id)
                        renderChoices()
                    }
                }
            }
        }
        renderChoices()
        content.addView(bookScroll, LinearLayout.LayoutParams(-1, host.dp(230f)))

        val dialog = AlertDialog.Builder(host)
            .setTitle("مجموعهٔ جدید")
            .setView(host.dialogPadding(content))
            .setNegativeButton("لغو", null)
            .setPositiveButton("ساخت", null)
            .create()
        val before = host.library.toString()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = TextNormalization.titleCaseWords(name.text.toString())
                if (title.isEmpty()) {
                    name.error = "نام مجموعه را وارد کن"
                    return@setOnClickListener
                }
                val collection = JSONObject()
                val id = UUID.randomUUID().toString()
                try {
                    collection.put("id", id).put("title", title)
                    collection.put("orderIndex", LibraryOrdering.nextOrderIndex(host.collectionArray()))
                    host.collectionArray().put(collection)
                    for (bookId in selected) {
                        host.findBookById(bookId)?.put("collectionId", id)
                    }
                } catch (_: JSONException) {
                }
                if (!host.saveLibrary()) {
                    host.restoreLibrary(before)
                    dialog.dismiss()
                    host.showDashboard()
                    return@setOnClickListener
                }
                host.activeCollectionId = id
                host.selectedCollectionIds.clear()
                dialog.dismiss()
                host.showBookShelf()
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun showEditCollectionDialog(collection: JSONObject?) {
        if (collection == null) return
        val collectionId = collection.optString("id")
        val name = host.edit("نام سری", false).apply {
            setText(collection.optString("title", ""))
            setSelection(length())
            if (collectionId == "other") isEnabled = false
        }
        val description = host.edit("توضیحات کوتاه", true).apply {
            setText(collection.optString("description", ""))
        }
        val form = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            addView(host.clearableInput(name), host.fieldParams())
            addView(host.clearableInput(description), host.fieldParams())
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("ویرایش سری")
            .setView(host.dialogPadding(form))
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = if (collectionId == "other") collection.optString("title")
                else TextNormalization.titleCaseWords(name.text.toString())
                if (title.isEmpty()) {
                    name.error = "نام سری را وارد کن"
                    return@setOnClickListener
                }
                val current = host.findCollectionById(collectionId)
                if (current == null) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                val before = host.library.toString()
                host.putJson(current, "title", title)
                host.putJson(current, "description", description.text.toString().trim())
                if (!host.saveLibrary()) {
                    host.restoreLibrary(before)
                    dialog.dismiss()
                    refreshCollectionUi()
                    return@setOnClickListener
                }
                dialog.dismiss()
                refreshCollectionUi()
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun refreshCollectionUi() {
        when (host.page) {
            "collections" -> refreshCollectionSelection()
            "collectionInfo" -> showCollectionInfo(host.findCollectionById(host.activeCollectionId))
            "collectionBooks" -> {
                val column = host.appColumn
                if (column != null) {
                    if (column.childCount > 0) column.removeViewAt(0)
                    host.addDashboardHeader()
                }
            }
        }
    }

    private fun selectionBookRow(
        title: String,
        selected: Boolean,
        unselectedIcon: String,
        iconSize: Int
    ): FrameLayout {
        val row = FrameLayout(host)
        row.background = host.roundDrawable(
            if (selected) {
                if (host.lightTheme) 0xFFEDE3FA.toInt() else 0xFF241B2C.toInt()
            } else {
                host.SURFACE
            },
            if (selected) host.PURPLE else 0xFF29242F.toInt(),
            10
        )
        val label = host.text(title, 14, host.TEXT).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(label, FrameLayout.LayoutParams(-1, -1))
        val mark = NativeIconView(host, if (selected) "check" else unselectedIcon,
            if (selected) host.PURPLE else host.MUTED)
        row.addView(mark, FrameLayout.LayoutParams(host.dp(iconSize.toFloat()),
            host.dp(iconSize.toFloat()), Gravity.LEFT or Gravity.CENTER_VERTICAL))
        return row
    }

    fun showCollections() {
        host.setPage("collections")
        host.selectedCollectionIds.clear()
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        addCollectionsHeader()

        val grid = GridView(host).apply {
            numColumns = 1
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = host.dp(10f)
            verticalSpacing = host.dp(10f)
            setSelector(android.R.color.transparent)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(host.BG)
            setPadding(host.dp(12f), host.dp(8f), host.dp(12f), host.dp(8f))
            clipToPadding = false
        }
        host.collectionsGrid = grid
        val adapter = CollectionCardAdapter(host)
        host.collectionAdapter = adapter
        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val collection = adapter.getCollection(position)
            if (host.selectedCollectionIds.isNotEmpty()) {
                toggleCollectionSelection(collection)
            } else {
                host.showAllBooksForCollection(collection.optString("id"))
            }
        }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, view, position, _ ->
            val collection = adapter.getCollection(position)
            val collectionId = collection.optString("id")
            if (host.selectedCollectionIds.contains(collectionId)) {
                host.startReorderDrag(view, "collection", collectionId)
            } else {
                toggleCollectionSelection(collection)
                true
            }
        }

        val frame = FrameLayout(host)
        frame.addView(grid, FrameLayout.LayoutParams(-1, -1))
        val empty = host.emptyLabel("هنوز مجموعه‌ای ساخته نشده")
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        grid.emptyView = empty
        host.appColumn.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun addCollectionsHeader() {
        val header: View
        if (host.selectedCollectionIds.isEmpty()) {
            header = host.makeHeader("کتابخانه مجموعه‌ها", "", "menu", "add",
                Runnable { host.openDrawer() }, Runnable { showCreateCollectionDialog() })
        } else {
            val singleSelection = host.selectedCollectionIds.size == 1
            val selectedId = if (singleSelection) host.selectedCollectionIds.first() else null
            header = host.makeHeader(host.selectedCollectionIds.size.toString() + " انتخاب‌شده", "",
                "close", if (singleSelection) "exclamation" else null,
                Runnable { clearCollectionSelection() },
                if (singleSelection) Runnable {
                    showCollectionInfo(host.findCollectionById(selectedId))
                } else null)
        }
        host.appColumn.addView(header, 0, LinearLayout.LayoutParams(-1, host.dp(62f)))
    }

    private fun toggleCollectionSelection(collection: JSONObject?) {
        if (collection == null) return
        val id = collection.optString("id")
        if (!host.selectedCollectionIds.add(id)) host.selectedCollectionIds.remove(id)
        refreshCollectionSelection()
    }

    private fun clearCollectionSelection() {
        host.selectedCollectionIds.clear()
        refreshCollectionSelection()
    }

    fun showCollectionInfo(collection: JSONObject?) {
        if (collection == null) return
        host.activeCollectionId = collection.optString("id", "other")
        host.setPage("collectionInfo")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        val header = host.makeHeader(collection.optString("title"), "", "menu", "edit",
            Runnable { host.openDrawer() },
            Runnable { showEditCollectionDialog(host.findCollectionById(host.activeCollectionId)) },
            10)
        host.appColumn.addView(header, LinearLayout.LayoutParams(-1, host.dp(62f)))
        host.titleView?.let { title ->
            val collectionTitle = collection.optString("title", "")
            title.contentDescription = "کپی نام مجموعه"
            title.setOnClickListener { host.copyToClipboard("collection", collectionTitle) }
        }

        val scroll = ScrollView(host).apply { isVerticalScrollBarEnabled = false }
        val body = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(18f), host.dp(18f), host.dp(24f))
        }
        val iconBox = FrameLayout(host).apply {
            background = host.roundDrawable(host.SURFACE, 0xFF40384A.toInt(), 22)
            addView(NativeIconView(host, "folder", host.PURPLE),
                FrameLayout.LayoutParams(host.dp(58f), host.dp(58f), Gravity.CENTER))
        }
        body.addView(iconBox, LinearLayout.LayoutParams(host.dp(92f), host.dp(92f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = host.dp(16f)
        })
        val booksCount = host.collectionBookCount(host.activeCollectionId)
        host.addBookInfoLine(body, "تعداد کتاب", booksCount.toString())
        var millis = 0L
        val allBooks = host.library.optJSONArray("books")
        if (allBooks != null) {
            for (index in 0 until allBooks.length()) {
                val book = allBooks.optJSONObject(index) ?: continue
                if (host.activeCollectionId != book.optString("collectionId", "other")) continue
                millis += host.readingStats?.totalForBook(book.optString("id")) ?: 0L
            }
        }
        val readPercent = LibraryMetrics.collectionReadPercent(collection, host.library.optJSONArray("books"))
        host.addBookInfoLine(body, "میزان مطالعه", if (booksCount == 0) "0" else "$readPercent٪")
        host.addBookInfoLine(body, "زمان مطالعه", host.formatReadingTime(millis))

        val descriptionText = collection.optString("description", "")
        val hasDescription = descriptionText.trim().isNotEmpty()
        val label = host.text("توضیحات کوتاه", 14, host.MUTED).apply { gravity = Gravity.RIGHT }
        body.addView(label, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = host.dp(12f)
        })
        body.addView(host.text(
            if (hasDescription) descriptionText else "توضیحی ثبت نشده",
            15,
            if (hasDescription) host.TEXT else host.MUTED
        ).apply {
            gravity = Gravity.RIGHT
            setPadding(0, host.dp(6f), 0, host.dp(14f))
        })
        body.addView(host.textButton("کتاب‌های این مجموعه", "book", host.PURPLE, Runnable {
            host.collectionBooksReturnPage = "collections"
            host.showAllBooksForCollection(host.activeCollectionId)
        }), LinearLayout.LayoutParams(-1, host.dp(50f)))
        scroll.addView(body)
        host.appColumn.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun refreshCollectionSelection() {
        if (host.page != "collections" || host.appColumn == null) return
        if (host.appColumn.childCount > 0) host.appColumn.removeViewAt(0)
        addCollectionsHeader()
        host.collectionAdapter?.notifyDataSetChanged()
    }
}
