package com.novelreader.app

import android.graphics.Color
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Owns the all-books and collection book shelves. */
internal class BookLibraryScreen(private val host: MainActivity) {
    fun showAllBooks() {
        host.setRootPage("allBooks")
        host.selectedBookIds.clear()
        host.activeGenreFilter.clear()
        host.activeCollectionId = "other"
        host.bookShelfSearchQuery = ""
        host.bookShelfReturnPage = "collections"
        showBookShelfPage(true)
    }

    fun showAllBooksForAuthor(author: String?) {
        host.setPage("allBooks")
        host.selectedAuthorNames.clear()
        host.selectedBookIds.clear()
        host.activeGenreFilter.clear()
        host.activeCollectionId = "other"
        host.bookShelfSearchQuery = author?.trim().orEmpty()
        host.bookShelfReturnPage = "authors"
        showBookShelfContent(true, null)
    }

    fun showAllBooksForCollection(collectionId: String) {
        val collection = host.findCollectionById(collectionId) ?: return
        host.setPage("allBooks")
        host.selectedBookIds.clear()
        host.activeGenreFilter.clear()
        host.activeCollectionId = "other"
        host.bookShelfSearchQuery = collection.optString("title", "")
        host.bookShelfReturnPage = "collections"
        showBookShelfContent(true, null)
    }

    fun showAllBooksForGenre(genre: String?) {
        host.setPage("allBooks")
        host.selectedBookIds.clear()
        host.activeCollectionId = "other"
        host.bookShelfSearchQuery = genre?.trim().orEmpty()
        host.bookShelfReturnPage = "genreInfo"
        host.activeGenreFilter.clear()
        showBookShelfContent(true, null)
    }

    fun showBookShelf() = showBookShelfPage(false)

    fun showBookShelfPage(allBooks: Boolean) = showBookShelfContent(allBooks, null)

    fun showBookShelfContent(allBooks: Boolean, author: String?) {
        val authorShelf = author != null
        if (allBooks || authorShelf) host.activeCollectionId = "other"
        if (authorShelf) host.bookShelfSearchQuery = author!!.trim()
        host.setPage(if (allBooks || authorShelf) "allBooks" else "collectionBooks")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        addDashboardHeader()

        val filters = LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(14f), host.dp(7f), host.dp(14f), host.dp(7f))
        }
        val search = host.edit("جستجو", false).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(host.bookShelfSearchQuery)
            setSelection(length())
        }
        host.searchField = search
        filters.addView(host.clearableInput(search), LinearLayout.LayoutParams(0, host.dp(48f), 1f))
        val genreFilter = host.iconButton(
            "filter", host.PURPLE, host.activeGenreFilter.isNotEmpty(),
            Runnable { host.showGenreFilter() }
        )
        host.genreFilterButton = genreFilter
        filters.addView(genreFilter, LinearLayout.LayoutParams(host.dp(48f), host.dp(48f)).apply {
            leftMargin = host.dp(8f)
        })
        host.appColumn.addView(filters)

        val grid = GridView(host).apply {
            numColumns = 2
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            horizontalSpacing = host.dp(10f)
            verticalSpacing = host.dp(10f)
            columnWidth = host.dp(150f)
            setSelector(android.R.color.transparent)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(host.BG)
            setPadding(host.dp(12f), host.dp(4f), host.dp(12f), host.dp(8f))
            clipToPadding = false
        }
        host.booksGrid = grid
        val adapter = BookCardAdapter(host)
        host.bookAdapter = adapter
        grid.adapter = adapter
        val listFrame = FrameLayout(host)
        listFrame.addView(grid, FrameLayout.LayoutParams(-1, -1))
        val empty = host.emptyLabel(
            if (allBooks || authorShelf) "کتابی در کتابخانه نیست" else "کتابی در این مجموعه نیست"
        )
        listFrame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        grid.emptyView = empty
        host.appColumn.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        val version = host.text("نسخهٔ " + host.appVersionName(), 10, host.MUTED).apply {
            gravity = Gravity.CENTER
        }
        host.appColumn.addView(version, LinearLayout.LayoutParams(-1, host.dp(22f)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                host.bookShelfSearchQuery = search.text.toString()
                host.bookAdapter?.notifyDataSetChanged()
            }
        })
    }

    fun onBookCardTap(book: JSONObject) {
        if (host.selectedBookIds.isNotEmpty()) {
            toggleBookSelection(book)
        } else {
            host.bookInfoReturnPage = host.page
            host.showBookInfo(book)
        }
    }

    fun toggleBookSelection(book: JSONObject?) {
        if (book == null) return
        val id = book.optString("id")
        if (!host.selectedBookIds.add(id)) host.selectedBookIds.remove(id)
        refreshDashboardSelection()
    }

    fun addDashboardHeader() {
        val collection = host.findCollectionById(host.activeCollectionId)
        val shelfTitle = if (host.showingAllBooks()) "کتابخانه کل"
        else collection?.optString("title", "سایر") ?: "سایر"
        val header: View
        if (host.selectedBookIds.isEmpty()) {
            val canEditSeries = !host.showingAllBooks() &&
                host.activeCollectionId != "other" && collection != null
            header = if (canEditSeries) {
                host.makeHeader(shelfTitle, "", "menu", "add",
                    Runnable { host.openDrawer() }, Runnable { host.showBookDialog(null) }, 10, 118)
            } else {
                host.makeHeader(shelfTitle, "", "menu", "add",
                    Runnable { host.openDrawer() }, Runnable { host.showBookDialog(null) }, 10)
            }
            if (canEditSeries && header is FrameLayout) {
                val editSeries = host.iconButton("edit", host.PURPLE, false,
                    Runnable { host.showEditCollectionDialog(host.findCollectionById(host.activeCollectionId)) })
                editSeries.contentDescription = "ویرایش سری"
                val editParams = FrameLayout.LayoutParams(host.dp(50f), host.dp(50f),
                    Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = host.dp(64f) }
                header.addView(editSeries, editParams)
            }
        } else {
            val label = host.selectedBookIds.size.toString() + " انتخاب‌شده"
            if (host.selectedBookIds.size == 1) {
                val selected = host.findBookById(host.selectedBookIds.first())
                header = host.makeHeader(label, "", "delete", "edit",
                    Runnable { confirmDeleteSelected() },
                    Runnable { if (selected != null) host.showBookDialog(selected) }, 10, 118)
            } else {
                header = host.makeHeader(label, "", "delete", null,
                    Runnable { confirmDeleteSelected() }, null)
            }
        }
        host.appColumn.addView(header, 0, LinearLayout.LayoutParams(-1, host.dp(62f)))
        if (host.selectedBookIds.isEmpty() && !host.showingAllBooks() &&
            host.activeCollectionId != "other" && collection != null
        ) {
            host.titleView?.let { title ->
                val collectionTitle = collection.optString("title", "")
                title.contentDescription = "کپی نام سری"
                title.setOnClickListener { host.copyToClipboard("series", collectionTitle) }
                host.applyPressScale(title, .98f)
            }
        }
    }

    fun refreshDashboardSelection() {
        if ((host.page != "collectionBooks" && host.page != "allBooks") || host.appColumn == null) return
        if (host.appColumn.childCount > 0) host.appColumn.removeViewAt(0)
        addDashboardHeader()
        host.bookAdapter?.notifyDataSetChanged()
    }

    fun confirmDeleteSelected() {
        if (host.selectedBookIds.isEmpty()) return
        val count = host.selectedBookIds.size
        val message = if (count == 1) "این کتاب حذف شود؟" else count.toString() + " کتاب حذف شوند؟"
        val dialog = android.app.AlertDialog.Builder(host)
            .setMessage(message)
            .setNegativeButton("لغو", null)
            .setPositiveButton("حذف", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val books = host.library.optJSONArray("books") ?: return@setOnClickListener
                val beforeDelete = host.library.toString()
                val removedCovers = mutableListOf<String>()
                val kept = JSONArray()
                for (index in 0 until books.length()) {
                    val book = books.optJSONObject(index)
                    if (book == null || !host.selectedBookIds.contains(book.optString("id"))) {
                        kept.put(book)
                    } else {
                        val cover = book.optString("cover", "")
                        if (cover.isNotEmpty()) removedCovers.add(cover)
                    }
                }
                host.putJson(host.library, "books", kept)
                if (host.saveLibrary()) {
                    host.selectedBookIds.clear()
                    for (cover in removedCovers) host.deleteCoverFile(cover)
                } else {
                    host.restoreLibrary(beforeDelete)
                }
                refreshDashboardSelection()
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
    }
}
