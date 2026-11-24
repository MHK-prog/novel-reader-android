package com.novelreader.app

import android.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import org.json.JSONArray

/** Owns the genre library list and its add/delete selection actions. */
internal class GenreLibraryScreen(private val host: MainActivity) {
    fun show() {
        host.setPage("genres")
        host.selectedGenreNames.clear()
        host.ensureGenreDetails()
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        addHeader()

        val grid = GridView(host).apply {
            numColumns = 1
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            horizontalSpacing = host.dp(10f)
            verticalSpacing = host.dp(10f)
            setSelector(android.R.color.transparent)
            cacheColorHint = android.graphics.Color.TRANSPARENT
            setBackgroundColor(host.BG)
            setPadding(host.dp(12f), host.dp(8f), host.dp(12f), host.dp(8f))
            clipToPadding = false
        }
        val adapter = GenreCardAdapter(host)
        host.genreAdapter = adapter
        host.genresGrid = grid
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val genre = adapter.getGenre(position)
            if (host.selectedGenreNames.isNotEmpty()) toggleSelection(genre)
            else host.showGenreInfo(genre)
        }
        grid.setOnItemLongClickListener { _, view, position, _ ->
            val genre = adapter.getGenre(position)
            if (host.selectedGenreNames.contains(genre)) {
                host.startReorderDrag(view, "genre", genre)
            } else {
                toggleSelection(genre)
                true
            }
        }
        val frame = FrameLayout(host)
        frame.addView(grid, FrameLayout.LayoutParams(-1, -1))
        frame.addView(host.emptyLabel("هنوز ژانری ثبت نشده"), FrameLayout.LayoutParams(-1, -1))
        grid.emptyView = frame.getChildAt(1)
        host.appColumn.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun addHeader() {
        val header = when {
            host.selectedGenreNames.isEmpty() -> host.makeHeader(
                "کتابخانه ژانرها", "", "menu", "add",
                Runnable { host.openDrawer() }, Runnable { showAddDialog() }
            )
            host.selectedGenreNames.size == 1 -> {
                val selected = host.selectedGenreNames.iterator().next()
                host.makeHeader("${host.selectedGenreNames.size} انتخاب", "", "delete", "edit",
                    Runnable { confirmDeleteSelected() },
                    Runnable { host.showEditGenreInfoDialog(selected) }, 10, 118)
            }
            else -> host.makeHeader("${host.selectedGenreNames.size} انتخاب", "", "delete", null,
                Runnable { confirmDeleteSelected() }, null, 10)
        }
        host.appColumn.addView(header, 0, LinearLayout.LayoutParams(-1, host.dp(62f)))
    }

    private fun toggleSelection(genre: String) {
        if (!host.selectedGenreNames.add(genre)) host.selectedGenreNames.remove(genre)
        refreshSelection()
    }

    fun refreshSelection() {
        if (host.page != "genres" || host.appColumn.childCount == 0) return
        host.appColumn.removeViewAt(0)
        addHeader()
        host.genreAdapter?.notifyDataSetChanged()
    }

    fun confirmDeleteSelected() {
        if (host.selectedGenreNames.isEmpty()) return
        val deleting = ArrayList(host.selectedGenreNames)
        val message = if (deleting.size == 1) {
            "ژانر «${deleting[0]}» از فهرست و کتاب‌های مرتبط حذف شود؟"
        } else {
            "${deleting.size} ژانر و پیوندهایشان از کتاب‌ها حذف شوند؟"
        }
        val dialog = AlertDialog.Builder(host).setMessage(message)
            .setNegativeButton("لغو", null)
            .setPositiveButton("حذف", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                for (genre in deleting) {
                    if (!host.deleteTag(genre, null, null)) return@setOnClickListener
                }
                host.selectedGenreNames.clear()
                dialog.dismiss()
                show()
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    fun showAddDialog() {
        val name = host.edit("نام ژانر", false)
        val dialog = AlertDialog.Builder(host).setTitle("ژانر جدید")
            .setView(host.dialogPadding(host.clearableInput(name)))
            .setNegativeButton("لغو", null)
            .setPositiveButton("افزودن", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val genre = host.canonicalGenre(TextNormalization.titleCaseWords(name.text.toString()))
                if (genre.isEmpty()) {
                    name.error = "نام ژانر را وارد کن"
                    return@setOnClickListener
                }
                if (host.findGenreName(host.allGenres(), genre) != null) {
                    name.error = "این ژانر از قبل وجود دارد"
                    return@setOnClickListener
                }
                val genres: JSONArray = host.library.optJSONArray("genres") ?: JSONArray()
                host.appendUniqueGenre(genres, genre)
                host.putJson(host.library, "genres", genres)
                host.ensureGenreDetail(genre)
                if (host.saveLibrary()) {
                    host.rebuildRecommendationWeights()
                    host.persistRecommendationState()
                    dialog.dismiss()
                    show()
                }
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
