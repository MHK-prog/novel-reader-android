package com.novelreader.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Locale

internal fun interface ColloquializerPairAction {
    @Throws(Exception::class)
    fun save(source: String, replacement: String, exceptions: List<String>, onlyThisChapter: Boolean)
}

/** Owns the replacement-rule settings page and its editing dialogs. */
internal class ColloquializerSettingsScreen(private val host: MainActivity) {
    fun show() {
        host.setPage("colloquializer")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        host.appColumn.addView(host.makeHeader(
            "محاوره‌ای‌ساز", "", "menu", "help",
            Runnable { host.openDrawer() }, Runnable { showHelp() }, 0, 68, true
        ))

        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(18f), host.dp(8f), host.dp(18f), host.dp(3f))
        }
        val installed = host.colloquializerStore.isInstalled()
        content.addView(host.textButton("انتخاب فایل JSON", "scan_text", host.PURPLE,
            Runnable { chooseRules() }), host.fieldParams())
        addControls(content)

        if (!installed) {
            val empty = host.text("هنوز فایل قوانینی انتخاب نشده است.", 14, host.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, host.dp(24f), 0, host.dp(20f))
            }
            host.appColumn.addView(content, LinearLayout.LayoutParams(-1, -2))
            host.appColumn.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
            return
        }

        try {
            val rules = readRules(JSONArray(host.colloquializerStore.readRules()))
            host.addPreferenceSwitch(content, "فعال‌سازی محاوره‌ای‌ساز", "extensions",
                "enabled.colloquializer", true)
            val count = host.text("", 12, host.MUTED).apply { gravity = Gravity.CENTER }
            val search = host.edit("جستجو در قانون‌ها", false).apply { isSingleLine = true }
            content.addView(count, LinearLayout.LayoutParams(-1, host.dp(27f)))
            content.addView(host.clearableInput(search), host.fieldParams())

            val visible = ArrayList<Int>()
            lateinit var refresh: () -> Unit
            val ruleList = ListView(host).apply {
                divider = null
                dividerHeight = host.dp(7f)
                setPadding(host.dp(18f), host.dp(5f), host.dp(18f), host.dp(8f))
                clipToPadding = false
                isVerticalScrollBarEnabled = false
                setBackgroundColor(host.BG)
            }
            val adapter = object : BaseAdapter() {
                override fun getCount(): Int = visible.size
                override fun getItem(position: Int): Any = rules[visible[position]]
                override fun getItemId(position: Int): Long = visible[position].toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row: LinearLayout
                    val sourceText: TextView
                    val replacementText: TextView
                    val editButton: FrameLayout
                    val deleteButton: FrameLayout
                    if (convertView == null) {
                        row = LinearLayout(host).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            layoutDirection = View.LAYOUT_DIRECTION_RTL
                            setPadding(host.dp(10f), host.dp(5f), host.dp(8f), host.dp(5f))
                            background = host.roundDrawable(host.SURFACE, 0xFF302A38.toInt(), 13)
                        }
                        val labels = LinearLayout(host).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        sourceText = host.text("", 14, host.TEXT).apply {
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setRtlAlignment()
                        }
                        replacementText = host.text("", 13, host.MUTED).apply {
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setRtlAlignment()
                        }
                        labels.addView(sourceText, LinearLayout.LayoutParams(-1, 0, 1f))
                        labels.addView(replacementText, LinearLayout.LayoutParams(-1, 0, 1f))
                        row.addView(labels, LinearLayout.LayoutParams(0, -1, 1f))
                        editButton = host.iconButton("edit", host.PURPLE, false, null)
                        deleteButton = host.iconButton("delete", MainActivity.RED, false, null)
                        row.addView(editButton, LinearLayout.LayoutParams(host.dp(44f), host.dp(48f)))
                        row.addView(deleteButton, LinearLayout.LayoutParams(host.dp(44f), host.dp(48f)))
                        row.tag = arrayOf(sourceText, replacementText, editButton, deleteButton)
                    } else {
                        row = convertView as LinearLayout
                        val views = row.tag as Array<View>
                        sourceText = views[0] as TextView
                        replacementText = views[1] as TextView
                        editButton = views[2] as FrameLayout
                        deleteButton = views[3] as FrameLayout
                    }

                    val ruleIndex = visible[position]
                    val rule = rules[ruleIndex]
                    sourceText.text = rule.source
                    var summary = "←  " + rule.replacement
                    if (rule.exceptions.isNotEmpty()) {
                        summary += " · بجز " + android.text.TextUtils.join("، ", rule.exceptions)
                    }
                    replacementText.text = summary
                    editButton.contentDescription = "ویرایش قانون " + rule.source
                    deleteButton.contentDescription = "حذف قانون " + rule.source
                    editButton.setOnClickListener { showEditRule(rules, ruleIndex, refresh) }
                    deleteButton.setOnClickListener { confirmDeleteRule(rules, ruleIndex, refresh) }
                    return row
                }
            }
            ruleList.adapter = adapter
            refresh = {
                val query = search.text.toString().trim().lowercase(Locale.ROOT)
                visible.clear()
                for (i in rules.indices) {
                    val rule = rules[i]
                    if (query.isEmpty() || rule.source.lowercase(Locale.ROOT).contains(query) ||
                        rule.replacement.lowercase(Locale.ROOT).contains(query) ||
                        android.text.TextUtils.join(" ", rule.exceptions).lowercase(Locale.ROOT).contains(query)
                    ) visible.add(i)
                }
                count.text = visible.size.toString() + " از " + rules.size + " قانون"
                adapter.notifyDataSetChanged()
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            refresh()

            val listFrame = FrameLayout(host)
            listFrame.addView(ruleList, FrameLayout.LayoutParams(-1, -1))
            val empty = host.emptyLabel("قانونی پیدا نشد.")
            listFrame.addView(empty, FrameLayout.LayoutParams(-1, -1))
            ruleList.emptyView = empty
            host.appColumn.addView(content, LinearLayout.LayoutParams(-1, -2))
            host.appColumn.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        } catch (_: Exception) {
            content.addView(host.text("فایل قوانین قابل خواندن نیست.", 14, MainActivity.RED), host.fieldParams())
            host.appColumn.addView(content, LinearLayout.LayoutParams(-1, -2))
        }
    }

    fun showPairDialog(
        title: String,
        initialSource: String?,
        initialExceptions: List<String>,
        showChapterOption: Boolean,
        saveAction: ColloquializerPairAction,
    ) {
        val source = colloquializerField("عبارت مبدأ").apply {
            setText(initialSource ?: "")
            setSelection(length())
        }
        val replacement = colloquializerField("عبارت مقصد")

        val fields = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val sourceColumn = fieldColumn("عبارت مبدأ", source)
        val replacementColumn = fieldColumn("عبارت مقصد", replacement)
        fields.addView(sourceColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = host.dp(8f)
        })
        fields.addView(replacementColumn, LinearLayout.LayoutParams(0, -2, 1f))

        val form = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        form.addView(fields, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = if (showChapterOption) host.dp(9f) else 0
        })
        val exceptionsField = colloquializerField("مثلاً شدن، داشته باشد").apply {
            isSingleLine = true
            setText(android.text.TextUtils.join("، ", initialExceptions))
        }
        form.addView(fieldColumn("استثناها (با ویرگول جدا کن)", exceptionsField),
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = if (showChapterOption) host.dp(9f) else 0
            })
        val onlyThisChapter = if (showChapterOption) CheckBox(host).apply {
            text = "فقط همین Chapter؛ در JSON ذخیره نشود"
            setTextColor(host.TEXT)
            typeface = host.vazir ?: android.graphics.Typeface.DEFAULT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else null
        if (onlyThisChapter != null) form.addView(onlyThisChapter, host.fieldParams())

        val dialog = AlertDialog.Builder(host)
            .setTitle(title)
            .setView(host.dialogPadding(form))
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newSource = source.text.toString()
                val newReplacement = replacement.text.toString()
                if (newSource.trim().isEmpty()) {
                    source.error = "عبارت مبدأ خالی است."
                    source.requestFocus()
                    return@setOnClickListener
                }
                val wildcard = newSource.indexOf('x')
                if (wildcard >= 0 && newSource.indexOf('x', wildcard + 1) >= 0) {
                    source.error = "هر الگو فقط می‌تواند یک x داشته باشد."
                    source.requestFocus()
                    return@setOnClickListener
                }
                try {
                    saveAction.save(newSource, newReplacement,
                        parseExceptions(exceptionsField.text.toString()), onlyThisChapter?.isChecked == true)
                    dialog.dismiss()
                } catch (error: Exception) {
                    val message = error.message
                    host.toast(if (message.isNullOrBlank()) "ذخیرهٔ جفت ناموفق بود." else message)
                }
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun importRules(uri: Uri) {
        val progressDialog = AlertDialog.Builder(host)
            .setMessage("در حال بررسی فایل قوانین…")
            .setCancelable(false)
            .create()
        progressDialog.show()
        host.styleDialogWindow(progressDialog)
        host.colloquializerExecutor.execute {
            var errorMessage: String? = null
            try {
                val input: InputStream = host.contentResolver.openInputStream(uri)
                    ?: throw IOException("فایل خوانده نشد.")
                input.use { source ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 10 * 1024 * 1024) throw IOException("حجم فایل بیشتر از ۱۰ مگابایت است.")
                            output.write(buffer, 0, count)
                        }
                        host.colloquializerStore.saveRules(output.toString("UTF-8"))
                    }
                }
            } catch (error: Exception) {
                errorMessage = error.message ?: "فایل JSON معتبر نیست."
            }
            val result = errorMessage
            host.runOnUiThread {
                if (host.isFinishing) return@runOnUiThread
                progressDialog.dismiss()
                if (result == null) host.toast("قوانین محاوره‌ای‌ساز ذخیره شد.")
                else host.toast("بارگذاری ناموفق بود: " + result)
                if (host.page == "colloquializer") show()
                else if (host.page == "reader") host.showReader()
            }
        }
    }

    private fun addControls(content: LinearLayout) {
        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        actions.addView(host.textButton("افزودن جفت", "add", host.PURPLE,
            Runnable { showAddRule() }), LinearLayout.LayoutParams(0, host.dp(48f), 1f))
        actions.addView(host.textButton("حذف قوانین", "delete", MainActivity.RED, Runnable {
            try {
                host.colloquializerStore.saveRules("[]")
                host.toast("قوانین جایگزینی پاک شدند.")
                show()
            } catch (error: Exception) {
                host.toast("پاک کردن قوانین ناموفق بود: " + (error.message ?: ""))
            }
        }), LinearLayout.LayoutParams(0, host.dp(48f), 1f).apply { leftMargin = host.dp(7f) })
        content.addView(actions, LinearLayout.LayoutParams(-1, host.dp(48f)).apply {
            bottomMargin = host.dp(7f)
        })
    }

    fun showHelp() {
        val help = host.text(
            "در هر قانون، عبارت مبدأ با عبارت مقصد جایگزین می‌شود. x یک رشته از حروف فارسی را می‌گیرد؛ s هر تعداد فاصله، نیم‌فاصله یا نبود فاصله را می‌پذیرد؛ n وجود یا نبود «ن» را پوشش می‌دهد.\n\n" +
                "استثناها را با ویرگول جدا کن تا قانون کنار آن عبارت اجرا نشود. هر متن فقط یک‌بار و بر اساس متن اصلی پردازش می‌شود؛ مقصد یک قانون دوباره وارد پردازش نمی‌شود. هنگام محاوره‌ای‌سازی، عبارت رسمی کنار تغییر به شکل {عبارت رسمی} ثبت می‌شود و از منوی Chapter می‌توانی نشانه‌ها را برداری یا متن اصلی را برگردانی.",
            14, host.TEXT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            setPadding(host.dp(8f), host.dp(10f), host.dp(8f), host.dp(10f))
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("راهنمای محاوره‌ای‌ساز")
            .setView(host.dialogPadding(help))
            .setPositiveButton("بستن", null)
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    private fun showEditRule(rules: ArrayList<ColloquializerRule>, index: Int, refresh: () -> Unit) {
        if (index !in rules.indices) return
        val current = rules[index]
        showPairDialog("ویرایش قانون جایگزینی", current.source, current.exceptions, false) {
                newSource, newReplacement, exceptions, _ ->
            val wildcard = newSource.indexOf('x')
            if (wildcard >= 0 && newSource.indexOf('x', wildcard + 1) >= 0) {
                throw IllegalArgumentException("هر الگو فقط می‌تواند یک x داشته باشد.")
            }
            for (i in rules.indices) {
                if (i != index && rules[i].source == newSource) {
                    throw IllegalArgumentException("این الگو از قبل در فهرست وجود دارد.")
                }
            }
            val updated = ArrayList(rules)
            updated[index] = ColloquializerRule(newSource, newReplacement, exceptions)
            host.colloquializerStore.saveRules(writeRules(updated).toString())
            rules.clear()
            rules.addAll(updated)
            refresh()
            host.toast("قانون به‌روزرسانی شد.")
        }
    }

    private fun showAddRule() {
        showPairDialog("افزودن جفت جایگزینی", "", emptyList(), false) {
                newSource, newReplacement, exceptions, _ ->
            val current = if (host.colloquializerStore.isInstalled())
                JSONArray(host.colloquializerStore.readRules()) else JSONArray()
            for (i in 0 until current.length()) {
                val pair = current.optJSONArray(i)
                if (pair != null && newSource == pair.optString(0)) {
                    throw IllegalArgumentException("این عبارت مبدأ از قبل وجود دارد.")
                }
            }
            val pair = JSONArray().put(newSource).put(newReplacement)
            if (exceptions.isNotEmpty()) pair.put(JSONArray(exceptions))
            current.put(pair)
            host.colloquializerStore.saveRules(current.toString())
            show()
            host.toast("جفت جایگزینی ذخیره شد.")
        }
    }

    private fun confirmDeleteRule(rules: ArrayList<ColloquializerRule>, index: Int, refresh: () -> Unit) {
        if (index !in rules.indices) return
        val rule = rules[index]
        val dialog = AlertDialog.Builder(host)
            .setTitle("حذف قانون؟")
            .setMessage(rule.source + "  ←  " + rule.replacement)
            .setNegativeButton("لغو", null)
            .setPositiveButton("حذف") { _, _ ->
                val updated = ArrayList(rules)
                updated.removeAt(index)
                try {
                    host.colloquializerStore.saveRules(writeRules(updated).toString())
                    rules.clear()
                    rules.addAll(updated)
                    refresh()
                    host.toast("قانون حذف شد.")
                } catch (error: Exception) {
                    host.toast("حذف قانون ناموفق بود: " + (error.message ?: ""))
                }
            }
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    private fun chooseRules() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
        }
        try {
            host.startActivityForResult(picker, MainActivity.REQUEST_COLLOQUIALIZER)
        } catch (_: Exception) {
            host.toast("انتخاب‌گر فایل در دسترس نیست.")
        }
    }

    private fun readRules(json: JSONArray): ArrayList<ColloquializerRule> {
        val rules = ArrayList<ColloquializerRule>(json.length())
        for (i in 0 until json.length()) {
            val pair = json.optJSONArray(i) ?: continue
            if (pair.length() < 2) continue
            val exceptions = ArrayList<String>()
            val exceptionArray = pair.optJSONArray(2)
            if (exceptionArray != null) {
                for (j in 0 until exceptionArray.length()) {
                    val value = exceptionArray.optString(j).trim()
                    if (value.isNotEmpty()) exceptions.add(value)
                }
            }
            rules.add(ColloquializerRule(pair.optString(0), pair.optString(1), exceptions))
        }
        return rules
    }

    private fun writeRules(rules: List<ColloquializerRule>): JSONArray {
        val json = JSONArray()
        for (rule in rules) {
            val pair = JSONArray().put(rule.source).put(rule.replacement)
            if (rule.exceptions.isNotEmpty()) pair.put(JSONArray(rule.exceptions))
            json.put(pair)
        }
        return json
    }

    private fun parseExceptions(value: String?): ArrayList<String> {
        val exceptions = LinkedHashSet<String>()
        if (value.isNullOrEmpty()) return ArrayList()
        for (part in value.split(Regex("[,،\\n]"))) {
            val exception = part.trim()
            if (exception.isNotEmpty()) exceptions.add(exception)
        }
        return ArrayList(exceptions)
    }

    private fun colloquializerField(hint: String): EditText = host.edit(hint, false).apply {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        background = host.roundDrawable(host.SURFACE_ALT, host.PURPLE, 15)
        setPadding(host.dp(14f), host.dp(9f), host.dp(14f), host.dp(9f))
        setRtlAlignment()
    }

    private fun fieldColumn(label: String, field: EditText): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val labelView = host.text(label, 12, host.MUTED).apply { setRtlAlignment() }
        addView(labelView, LinearLayout.LayoutParams(-1, host.dp(22f)))
        addView(host.clearableInput(field), LinearLayout.LayoutParams(-1, host.dp(48f)))
    }

    private fun TextView.setRtlAlignment() {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val vertical = gravity and Gravity.VERTICAL_GRAVITY_MASK
        gravity = Gravity.RIGHT or (if (vertical == 0) Gravity.CENTER_VERTICAL else vertical)
        textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        textDirection = View.TEXT_DIRECTION_RTL
    }
}
