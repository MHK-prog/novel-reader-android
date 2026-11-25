package com.novelreader.app

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.UUID

internal fun interface BookTextEntryAction {
    fun save(value: String)
}

internal fun interface BookGenreSelectionAction {
    fun save(selected: LinkedHashSet<String>, available: ArrayList<String>)
}

/** Builds and saves the add/edit book form. */
internal class BookEditorDialogScreen(private val host: MainActivity) {
    fun show(existing: JSONObject?) {
        val isNew = existing == null
        val noAiAccess = host.getSharedPreferences("general", android.content.Context.MODE_PRIVATE)
            .getBoolean("no_ai_access", false)
        val compactNewBookForm = isNew && !noAiAccess
        val originalCoverName = if (isNew) "" else existing?.optString("cover", "").orEmpty()
        val sourcePage = host.page
        val libraryBeforeSave = host.library.toString()
        val coverCommitted = booleanArrayOf(false)
        host.pendingCoverName = originalCoverName

        val form = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(8f), host.dp(4f), host.dp(8f), host.dp(4f))
        }
        val detailsPanel = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val coverRow = LinearLayout(host).apply { gravity = Gravity.CENTER }
        val previewFrame = FrameLayout(host).apply {
            background = host.roundDrawable(host.SURFACE_ALT, 0xFF40384A.toInt(), 10)
            clipToOutline = true
        }
        val coverPreview = ImageView(host).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        host.bookCoverPreview = coverPreview
        previewFrame.addView(coverPreview, FrameLayout.LayoutParams(-1, -1))
        val placeholder = LinearLayout(host).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }
        placeholder.addView(NativeIconView(host, "image", host.PURPLE),
            LinearLayout.LayoutParams(host.dp(30f), host.dp(30f)))
        placeholder.addView(host.text("انتخاب جلد", 13, host.PURPLE).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = host.dp(8f) })
        previewFrame.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
        previewFrame.tag = placeholder
        previewFrame.setOnClickListener { host.chooseBookCover() }
        host.applyPressScale(previewFrame, .97f)
        previewFrame.contentDescription = "انتخاب جلد کتاب"
        coverRow.addView(previewFrame, FrameLayout.LayoutParams(host.dp(128f), host.dp(182f), Gravity.CENTER))
        host.coverRemoveButton = host.iconButton("close", host.TEXT, true, Runnable {
            host.pendingCoverName = ""
            host.renderCoverPreview(coverPreview, "")
            host.coverRemoveButton?.visibility = View.GONE
        })
        previewFrame.addView(host.coverRemoveButton, FrameLayout.LayoutParams(host.dp(36f), host.dp(36f),
            Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = host.dp(5f)
            rightMargin = host.dp(5f)
        })
        host.coverRemoveButton?.visibility = if (originalCoverName.isEmpty()) View.GONE else View.VISIBLE
        detailsPanel.addView(coverRow, LinearLayout.LayoutParams(-1, host.dp(194f)).apply {
            bottomMargin = host.dp(9f)
        })
        host.renderCoverPreview(coverPreview, host.pendingCoverName.orEmpty())

        val title = host.edit("نام کتاب", false).apply { setText(if (isNew) "" else existing?.optString("title").orEmpty()) }
        val author = host.edit("نام نویسنده", false).apply { setText(if (isNew) "" else existing?.optString("author").orEmpty()) }
        val descriptionValue = arrayOf(if (isNew) "" else existing?.optString("description", "").orEmpty())
        val descriptionEntry = arrayOfNulls<View>(1)
        descriptionEntry[0] = host.bookDialogEntryButton("توضیحات کوتاه",
            host.descriptionButtonSummary(descriptionValue[0]), "edit", Runnable {
                host.showBookTextEntryDialog("توضیحات کوتاه کتاب", "توضیحات کوتاه کتاب", descriptionValue[0],
                    BookTextEntryAction { value ->
                        descriptionValue[0] = value
                        host.updateBookDialogEntryButton(descriptionEntry[0], host.descriptionButtonSummary(value))
                    })
            })
        val count = host.edit("تعداد Chapter", false).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if (isNew) "" else existing?.let { ChapterCatalog.largestWholeChapterNumber(it).toString() }.orEmpty())
        }
        val titleAuthorFields = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            addView(host.clearableInput(title), host.fieldParams())
            addView(host.clearableInput(author), host.fieldParams())
        }
        if (compactNewBookForm) form.addView(titleAuthorFields, LinearLayout.LayoutParams(-1, -2))
        else detailsPanel.addView(titleAuthorFields, LinearLayout.LayoutParams(-1, -2))

        if (compactNewBookForm) {
            val quickActions = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                clipChildren = false
            }
            quickActions.addView(host.textButton("ساخت پرامپت", "help", host.PURPLE,
                Runnable { host.showBookJsonPrompt() }), LinearLayout.LayoutParams(0, host.dp(50f), 1f))
            quickActions.addView(host.textButton("ورود پاسخ JSON", "scan_text", host.PURPLE,
                Runnable { host.chooseBookJson() }), LinearLayout.LayoutParams(0, host.dp(50f), 1f).apply {
                leftMargin = host.dp(7f)
            })
            form.addView(quickActions, LinearLayout.LayoutParams(-1, host.dp(50f)).apply {
                bottomMargin = host.dp(8f)
            })
            val detailsButton = host.text("جزئیات بیشتر", 14, host.TEXT).apply {
                gravity = Gravity.CENTER
                background = host.roundDrawable(host.SURFACE, 0xFF40384A.toInt(), 12)
            }
            form.addView(detailsButton, LinearLayout.LayoutParams(-1, host.dp(44f)).apply {
                bottomMargin = host.dp(8f)
            })
            detailsPanel.visibility = View.GONE
            detailsButton.setOnClickListener { host.showBookDetailsDialog(detailsPanel) }
            host.applyPressScale(detailsButton)
        }

        detailsPanel.addView(descriptionEntry[0], LinearLayout.LayoutParams(-1, host.dp(58f)).apply {
            bottomMargin = host.dp(9f)
        })
        detailsPanel.addView(host.clearableInput(count), host.fieldParams())

        var initialCollection = if (isNew) host.activeCollectionId else existing?.optString("collectionId", "other") ?: "other"
        if (host.findCollectionById(initialCollection) == null) initialCollection = "other"
        val chosenCollectionId = arrayOf(initialCollection)
        val previousCollectionId = arrayOf(initialCollection)
        val collectionField = FrameLayout(host).apply {
            background = host.roundDrawable(host.SURFACE_ALT, 0xFF40384A.toInt(), 12)
        }
        val collectionChoice = host.text("", 14, host.TEXT).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            setPadding(host.dp(48f), 0, host.dp(12f), 0)
        }
        collectionField.addView(collectionChoice, FrameLayout.LayoutParams(-1, -1))
        collectionField.addView(NativeIconView(host, "dropdown", host.PURPLE),
            FrameLayout.LayoutParams(host.dp(22f), host.dp(22f), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply {
                leftMargin = host.dp(10f)
            })
        val seriesNumber = host.edit("شمارهٔ کتاب در مجموعه", false).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val seriesNumberRow = host.clearableInput(seriesNumber)
        val initialNumber = if (!isNew && initialCollection != "other") host.seriesBookNumber(existing!!)
            else if (initialCollection != "other") host.nextSeriesBookNumber(initialCollection) else 0
        seriesNumber.setText(if (initialNumber > 0) initialNumber.toString() else "")
        val refreshCollectionChoice = Runnable {
            val collection = host.findCollectionById(chosenCollectionId[0])
            collectionChoice.text = "مجموعه: " + (collection?.optString("title", "سایر") ?: "سایر")
            val isSeries = chosenCollectionId[0] != "other"
            seriesNumberRow.visibility = if (isSeries) View.VISIBLE else View.GONE
            if (chosenCollectionId[0] != previousCollectionId[0]) {
                seriesNumber.setText(if (isSeries) host.nextSeriesBookNumber(chosenCollectionId[0]).toString() else "")
                previousCollectionId[0] = chosenCollectionId[0]
            }
        }
        refreshCollectionChoice.run()
        collectionField.setOnClickListener { host.showCollectionPicker(chosenCollectionId, refreshCollectionChoice) }
        detailsPanel.addView(collectionField, host.fieldParams())
        detailsPanel.addView(seriesNumberRow, host.fieldParams())

        val selected = LinkedHashSet<String>()
        val oldGenres = if (isNew) null else existing?.optJSONArray("genres")
        if (oldGenres != null) for (i in 0 until oldGenres.length()) selected.add(oldGenres.optString(i))
        val availableGenres = ArrayList<String>()
        val existingGenres = host.allGenres()
        for (i in 0 until existingGenres.length()) availableGenres.add(existingGenres.optString(i))
        val genreEntry = arrayOfNulls<View>(1)
        genreEntry[0] = host.bookDialogEntryButton("ژانرها", host.genreButtonSummary(selected), "tag", Runnable {
            host.showBookGenreSelectionDialog(availableGenres, selected,
                BookGenreSelectionAction { newSelected, newAvailable ->
                    selected.clear()
                    selected.addAll(newSelected)
                    availableGenres.clear()
                    availableGenres.addAll(newAvailable)
                    host.updateBookDialogEntryButton(genreEntry[0], host.genreButtonSummary(selected))
                })
        })
        detailsPanel.addView(genreEntry[0], LinearLayout.LayoutParams(-1, host.dp(58f)).apply {
            bottomMargin = host.dp(9f)
        })

        host.activeBookJsonImportTarget = BookJsonImportTarget(title, author, count, seriesNumber,
            descriptionValue, chosenCollectionId, selected, availableGenres, descriptionEntry[0]!!,
            genreEntry[0]!!, refreshCollectionChoice, Runnable { host.showBookDetailsDialog(detailsPanel) },
            isNew, coverPreview, host.coverRemoveButton!!, originalCoverName)
        if (!compactNewBookForm) form.addView(detailsPanel, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(host).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addView(form, ScrollView.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle(if (isNew) "افزودن کتاب" else "ویرایش کتاب")
            .setView(host.dialogPadding(scroll))
            .setNegativeButton("لغو", null)
            .setPositiveButton("ثبت اطلاعات", null)
            .create()
        dialog.setOnDismissListener {
            val pendingCover = host.pendingCoverName
            if (!coverCommitted[0] && !pendingCover.isNullOrEmpty() && pendingCover != originalCoverName) {
                host.deleteCoverFile(pendingCover)
            }
            val target = host.activeBookJsonImportTarget
            if (target != null && target.title === title) {
                if (!coverCommitted[0]) host.removeJsonCreatedCollections(target)
                host.activeBookJsonImportTarget = null
            }
            host.bookCoverPreview = null
            host.coverRemoveButton = null
        }
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            host.activeBookJsonImportTarget?.takeIf { it.title === title }?.saveButton = saveButton
            saveButton.setOnClickListener {
                val bookTitle = TextNormalization.titleCaseWords(title.text.toString())
                if (bookTitle.isEmpty()) {
                    title.error = "نام کتاب را وارد کن"
                    return@setOnClickListener
                }
                val bookAuthor = TextNormalization.titleCaseWords(author.text.toString())
                var chapterTotal = 0
                val chapterCountValue = count.text.toString().trim()
                if (isNew || chapterCountValue.isNotEmpty()) {
                    if (chapterCountValue.isEmpty()) {
                        count.error = "تعداد Chapter را وارد کن"
                        if (compactNewBookForm) host.showBookDetailsDialog(detailsPanel)
                        return@setOnClickListener
                    }
                    chapterTotal = chapterCountValue.toIntOrNull() ?: run {
                        count.error = "تعداد Chapter معتبر نیست"
                        if (compactNewBookForm) host.showBookDetailsDialog(detailsPanel)
                        return@setOnClickListener
                    }
                    val minimum = if (isNew) 1 else 0
                    if (chapterTotal < minimum || chapterTotal > 5000) {
                        count.error = "تعداد باید بین " + minimum + " تا 5000 باشد"
                        if (compactNewBookForm) host.showBookDetailsDialog(detailsPanel)
                        return@setOnClickListener
                    }
                }
                var bookNumber = 0
                if (chosenCollectionId[0] != "other") {
                    val value = seriesNumber.text.toString().trim()
                    bookNumber = if (value.isEmpty()) host.nextSeriesBookNumber(chosenCollectionId[0])
                    else value.toIntOrNull() ?: run {
                        seriesNumber.error = "شمارهٔ کتاب معتبر نیست"
                        return@setOnClickListener
                    }
                    if (bookNumber !in 1..9999) {
                        seriesNumber.error = "شماره باید بین 1 تا 9999 باشد"
                        return@setOnClickListener
                    }
                }
                val normalizedSelected = LinkedHashSet<String>()
                for (value in selected) {
                    val normalized = host.canonicalGenre(TextNormalization.titleCaseWords(value))
                    if (normalized.isNotEmpty()) normalizedSelected.add(normalized)
                }
                val savedGenres = JSONArray()
                normalizedSelected.forEach(savedGenres::put)
                var globalGenres = host.library.optJSONArray("genres") ?: JSONArray()
                normalizedSelected.forEach { host.appendUniqueGenre(globalGenres, it) }
                host.putJson(host.library, "genres", globalGenres)

                if (isNew) {
                    val chapters = JSONArray()
                    for (i in 0 until chapterTotal) {
                        val chapter = ChapterCatalog.newChapter()
                        host.putJson(chapter, "title", "Chapter " + (i + 1))
                        host.putJson(chapter, "storageIndex", i + 1)
                        host.putJson(chapter, "orderIndex", i)
                        chapters.put(chapter)
                    }
                    val book = JSONObject()
                    try {
                        book.put("id", UUID.randomUUID().toString())
                        book.put("recommendationId", host.nextRecommendationId())
                        book.put("title", bookTitle)
                        book.put("author", bookAuthor)
                        book.put("description", descriptionValue[0])
                        book.put("genres", savedGenres)
                        book.put("cover", host.pendingCoverName ?: "")
                        book.put("collectionId", chosenCollectionId[0])
                        if (bookNumber > 0) book.put("seriesNumber", bookNumber)
                        book.put("orderIndex", LibraryOrdering.nextOrderIndex(host.library.optJSONArray("books")))
                        book.put("chapters", chapters)
                        host.library.getJSONArray("books").put(book)
                    } catch (_: JSONException) { }
                    if (!host.saveLibrary()) {
                        host.restoreLibrary(libraryBeforeSave)
                        dialog.dismiss()
                        host.showBookShelf()
                        return@setOnClickListener
                    }
                    coverCommitted[0] = true
                    host.rebuildRecommendationWeights()
                    host.persistRecommendationState()
                    host.activeCollectionId = chosenCollectionId[0]
                    host.activeBookId = book.optString("id")
                    host.bookInfoReturnPage = if (sourcePage == "allBooks") "allBooks" else "collectionBooks"
                    host.chaptersReturnPage = host.bookInfoReturnPage
                    dialog.dismiss()
                    host.showChapters()
                } else {
                    val book = existing!!
                    host.putJson(book, "title", bookTitle)
                    host.putJson(book, "author", bookAuthor)
                    host.putJson(book, "description", descriptionValue[0])
                    host.putJson(book, "genres", savedGenres)
                    host.putJson(book, "cover", host.pendingCoverName ?: "")
                    host.putJson(book, "collectionId", chosenCollectionId[0])
                    if (bookNumber > 0) host.putJson(book, "seriesNumber", bookNumber)
                    else book.remove("seriesNumber")
                    if (chapterTotal > 0) ChapterCatalog.addMissingWholeChapters(book, chapterTotal)
                    if (!host.saveLibrary()) {
                        host.restoreLibrary(libraryBeforeSave)
                        dialog.dismiss()
                        when (sourcePage) {
                            "chapters" -> host.showChapters()
                            "bookInfo" -> host.showBookInfo(host.activeBook())
                            else -> host.showDashboard()
                        }
                        return@setOnClickListener
                    }
                    coverCommitted[0] = true
                    host.rebuildRecommendationWeights()
                    host.persistRecommendationState()
                    if (sourcePage == "chapters") host.activeCollectionId = chosenCollectionId[0]
                    if (originalCoverName.isNotEmpty() && originalCoverName != host.pendingCoverName) {
                        host.deleteCoverFile(originalCoverName)
                    }
                    dialog.dismiss()
                    when (sourcePage) {
                        "chapters" -> host.showChapters()
                        "bookInfo" -> host.showBookInfo(book)
                        else -> host.bookAdapter?.notifyDataSetChanged()
                    }
                }
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}
