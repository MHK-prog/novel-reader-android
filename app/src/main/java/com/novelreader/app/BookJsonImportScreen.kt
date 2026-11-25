package com.novelreader.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Handles JSON book import, preview, and the editable prompt. */
internal class BookJsonImportScreen(private val host: MainActivity) {
    fun chooseBookJson() {
        if (host.activeBookJsonImportTarget == null) {
            host.toast("فرم کتاب باز نیست.")
            return
        }
        showInputDialog()
    }

    fun showInputDialog() {
        val input = host.edit("محتوای JSON را اینجا بچسبان", true).apply {
            isSingleLine = false
            setInputType(android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            minLines = 8
            maxLines = 12
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = false
            gravity = Gravity.TOP or Gravity.LEFT
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(host.dp(14f), host.dp(12f), host.dp(14f), host.dp(12f))
            background = host.roundDrawable(host.SURFACE_ALT, 0xFF352D3F.toInt(), 15)
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("ورود اطلاعات JSON")
            .setView(host.dialogPadding(input))
            .setNegativeButton("لغو", null)
            .setNeutralButton("انتخاب فایل موجود", null)
            .setPositiveButton("اعمال متن", null)
            .create()
        host.bookJsonInputDialog = dialog
        dialog.setOnDismissListener {
            if (host.bookJsonInputDialog === dialog) host.bookJsonInputDialog = null
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { openFilePicker() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val imported = try {
                    BookJsonParser.parseText(input.text.toString())
                } catch (_: Exception) {
                    host.toast("JSON معتبر نیست؛ متن را بررسی کن.")
                    return@setOnClickListener
                }
                val target = host.activeBookJsonImportTarget
                if (target == null) {
                    host.toast("فرم کتاب بسته شده است.")
                    dialog.dismiss()
                    return@setOnClickListener
                }
                dialog.dismiss()
                showPreview(imported, target)
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun importJson(uri: Uri) {
        try {
            val input = host.contentResolver.openInputStream(uri) ?: throw IOException("Empty input stream")
            val imported = input.use { BookJsonParser.parseText(readUtf8(it)) }
            val target = host.activeBookJsonImportTarget
            if (target == null) {
                host.toast("فرم کتاب بسته شده است.")
                return
            }
            showPreview(imported, target)
        } catch (_: Exception) {
            host.toast("خواندن JSON ناموفق بود؛ ساختار فایل را بررسی کن.")
        }
    }

    fun showPromptSettings() {
        val promptEditor = host.edit("متن پرایمت", true).apply {
            setText(host.bookJsonPromptStore.load())
            minLines = 12
            maxLines = 22
            gravity = Gravity.RIGHT or Gravity.TOP
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            isVerticalScrollBarEnabled = true
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("متن پرایمت ورود JSON")
            .setView(host.dialogPadding(promptEditor))
            .setNegativeButton("لغو", null)
            .setNeutralButton("پیش‌فرض", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                promptEditor.setText(host.bookJsonPromptStore.defaultPrompt())
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = promptEditor.text.toString().trim()
                if (value.isEmpty()) {
                    promptEditor.error = "متن پرایمت نمی‌تواند خالی باشد"
                    return@setOnClickListener
                }
                host.bookJsonPromptStore.save(value)
                dialog.dismiss()
                host.toast("متن پرایمت ذخیره شد.")
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun showPrompt() {
        var prompt = host.bookJsonPromptStore.load()
        if (prompt.trim().isEmpty()) {
            host.toast("متن پرایمت خالی است؛ آن را در تنظیمات عمومی بنویس.")
            return
        }
        val target = host.activeBookJsonImportTarget
        if (target != null) {
            prompt = host.bookJsonPromptStore.fillField(prompt, "عنوان کتاب:", target.title.text.toString())
            prompt = host.bookJsonPromptStore.fillField(prompt, "نویسنده:", target.author.text.toString())
            val collection = if (target.collectionId[0] == "other") null
                else host.findCollectionById(target.collectionId[0])
            prompt = host.bookJsonPromptStore.fillField(prompt, "مجموعه:",
                collection?.optString("title", "").orEmpty())
        }
        val promptText = prompt
        val content = host.text(promptText, 14, host.TEXT).apply {
            setTextIsSelectable(true)
            gravity = Gravity.RIGHT or Gravity.TOP
            setPadding(host.dp(14f), host.dp(12f), host.dp(14f), host.dp(12f))
            setRtlAlignment()
        }
        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("نمونه برای هوش مصنوعی")
            .setView(host.dialogPadding(scroll))
            .setNegativeButton("بستن", null)
            .setNeutralButton("اشتراک‌گذاری") { _, _ -> sharePrompt(promptText) }
            .setPositiveButton("کپی متن") { _, _ ->
                host.copyToClipboard("book-json-prompt", promptText)
                host.toast("متن نمونه کپی شد.")
            }
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    fun removeCreatedCollections(target: BookJsonImportTarget?) {
        if (target == null || target.createdCollectionIds.isEmpty()) return
        val current = host.collectionArray()
        val kept = JSONArray()
        var removed = false
        for (i in 0 until current.length()) {
            val collection = current.optJSONObject(i)
            if (collection != null && target.createdCollectionIds.contains(collection.optString("id"))) {
                removed = true
            } else if (collection != null) {
                kept.put(collection)
            }
        }
        target.createdCollectionIds.clear()
        if (!removed) return
        host.putJson(host.library, "collections", kept)
        host.saveLibrary()
    }

    private fun openFilePicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            host.startActivityForResult(picker, MainActivity.REQUEST_BOOK_JSON)
        } catch (_: Exception) {
            host.toast("انتخاب فایل JSON در دسترس نیست.")
        }
    }

    private fun readUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun showPreview(data: BookJsonData, target: BookJsonImportTarget) {
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12f), host.dp(6f), host.dp(12f), host.dp(8f))
        }
        addPreviewSection(content, "دریافتی از JSON", host.PURPLE)
        addPreviewRow(content, "نام کتاب", if (data.hasTitle) data.title else target.title.text.toString(), data.hasTitle)
        addPreviewRow(content, "نام نویسنده", if (data.hasAuthor) data.author else target.author.text.toString(), data.hasAuthor)
        addPreviewRow(content, "لینک جلد", if (data.hasCoverUrl) data.coverUrl else "بدون تغییر", data.hasCoverUrl)
        addPreviewRow(content, "توضیحات", if (data.hasDescription) shortPreview(data.description)
            else target.description[0], data.hasDescription)
        val currentGenres = TextUtils.join("، ", target.selectedGenres)
        addPreviewRow(content, "ژانرها", if (data.hasGenres) TextUtils.join("، ", data.genres)
            else currentGenres, data.hasGenres)
        addPreviewRow(content, "تعداد Chapter", if (data.hasChapterCount) data.chapterCount.toString()
            else target.count.text.toString(), data.hasChapterCount)
        val currentCollection = host.findCollectionById(target.collectionId[0])
        val collectionValue = currentCollection?.optString("title", "سایر") ?: "سایر"
        addPreviewRow(content, "مجموعه", if (data.hasCollection && data.collection.isNotEmpty())
            data.collection else collectionValue, data.hasCollection && data.collection.isNotEmpty())
        addPreviewRow(content, "شماره در مجموعه", if (data.hasSeriesNumber) data.seriesNumber.toString()
            else target.seriesNumber.text.toString(), data.hasSeriesNumber)
        content.addView(host.text("بنفش: مقدار استخراج‌شده از JSON   ·   خاکستری: مقدار فعلی فرم", 12,
            host.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(host.dp(4f), host.dp(8f), host.dp(4f), host.dp(2f))
        }, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(host).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            addView(content)
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("بازبینی اطلاعات JSON")
            .setView(host.dialogPadding(scroll))
            .setNegativeButton("لغو", null)
            .setPositiveButton("اعمال تغییرات", null)
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setLayout(
            minOf(host.resources.displayMetrics.widthPixels - host.dp(32f), host.dp(500f)),
            minOf(host.resources.displayMetrics.heightPixels - host.dp(160f), host.dp(540f))
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.dismiss()
            applyBookJson(data, target)
        }
    }

    private fun addPreviewSection(parent: LinearLayout, label: String, color: Int) {
        parent.addView(host.text(label, 14, color).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setPadding(host.dp(2f), host.dp(5f), host.dp(2f), host.dp(6f))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addPreviewRow(parent: LinearLayout, label: String, value: String?, extracted: Boolean) {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12f), host.dp(8f), host.dp(12f), host.dp(8f))
            background = host.roundDrawable(host.SURFACE,
                if (extracted) host.PURPLE else 0xFF39343F.toInt(), 11)
        }
        val titleRow = LinearLayout(host).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(host.text(label, 13, host.TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(host.text(if (extracted) "JSON" else "فعلی", 11,
            if (extracted) host.PURPLE else host.MUTED))
        row.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        val cleanValue = value?.trim().orEmpty()
        row.addView(host.text(if (cleanValue.isEmpty()) "خالی" else cleanValue, 14,
            if (cleanValue.isEmpty()) host.MUTED else host.TEXT).apply {
            gravity = Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = host.dp(4f) })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = host.dp(6f) })
    }

    private fun shortPreview(value: String?): String {
        val preview = value.orEmpty().replace('\n', ' ').trim()
        if (preview.isEmpty()) return "خالی"
        return if (preview.length <= 150) preview else preview.substring(0, 147) + "…"
    }

    private fun findCollectionIdByTitle(title: String): String? {
        val collections = host.collectionArray()
        for (i in 0 until collections.length()) {
            val collection = collections.optJSONObject(i)
            if (collection != null && collection.optString("title", "").trim()
                    .equals(title.trim(), ignoreCase = true)) {
                return collection.optString("id", "other")
            }
        }
        return null
    }

    private fun createCollection(title: String, target: BookJsonImportTarget): String? {
        val collectionTitle = TextNormalization.titleCaseWords(title)
        if (collectionTitle.isEmpty()) return null
        findCollectionIdByTitle(collectionTitle)?.let { return it }
        val id = UUID.randomUUID().toString()
        return try {
            val collections = host.collectionArray()
            collections.put(JSONObject().put("id", id).put("title", collectionTitle)
                .put("orderIndex", LibraryOrdering.nextOrderIndex(collections)))
            target.createdCollectionIds.add(id)
            id
        } catch (_: JSONException) {
            null
        }
    }

    private fun applyBookJson(data: BookJsonData, target: BookJsonImportTarget) {
        if (host.activeBookJsonImportTarget !== target) {
            host.toast("فرم کتاب بسته شده است.")
            return
        }
        if (data.hasTitle) target.title.setText(data.title)
        if (data.hasAuthor) target.author.setText(data.author)
        if (data.hasDescription) {
            target.description[0] = data.description
            host.updateBookDialogEntryButton(target.descriptionEntry,
                host.descriptionButtonSummary(data.description))
        }
        if (data.hasChapterCount && data.chapterCount in 1..5000) {
            target.count.setText(data.chapterCount.toString())
        }
        if (data.hasSeriesNumber && data.seriesNumber in 1..9999) {
            target.seriesNumber.setText(data.seriesNumber.toString())
        }
        if (data.hasGenres) {
            target.selectedGenres.clear()
            for (raw in data.genres) host.addGenreSelection(raw, target.availableGenres, target.selectedGenres)
            host.updateBookDialogEntryButton(target.genreEntry,
                host.genreButtonSummary(target.selectedGenres))
        }
        if (data.hasCollection && data.collection.isNotEmpty()) {
            val matchingCollection = findCollectionIdByTitle(data.collection)
                ?: createCollection(data.collection, target)
            if (matchingCollection != null) {
                target.collectionId[0] = matchingCollection
                target.refreshCollectionChoice.run()
            }
        }
        if (data.hasCoverUrl) host.downloadBookCover(data.coverUrl, target)
        host.toast("اطلاعات اضافه شد؛ جزئیات را بررسی کن.")
        target.showDetails.run()
    }

    private fun sharePrompt(prompt: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "قالب اطلاعات کتاب برای JSON")
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        try {
            host.startActivity(Intent.createChooser(share, "اشتراک‌گذاری نمونه"))
        } catch (_: Exception) {
            host.toast("اشتراک‌گذاری در دسترس نیست.")
        }
    }

    private fun TextView.setRtlAlignment() {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.RIGHT
        textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        textDirection = View.TEXT_DIRECTION_RTL
    }
}
