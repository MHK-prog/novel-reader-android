package com.novelreader.app

import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashSet

class ResumeTarget(
    @JvmField val bookId: String,
    @JvmField val collectionId: String,
    @JvmField val storageIndex: Int,
    @JvmField val chapterPosition: Int,
)

class ColloquializerRule(source: String, replacement: String, exceptions: List<String>) {
    @JvmField val source: String = source
    @JvmField val replacement: String = replacement
    @JvmField val exceptions: ArrayList<String> = ArrayList(exceptions)
}

class NavigationState {
    @JvmField var page: String? = null
    @JvmField var activeBookId: String? = null
    @JvmField var activeChapter: Int = 0
    @JvmField var activeCollectionId: String? = null
    @JvmField var activeAuthor: String? = null
    @JvmField var activeGenre: String? = null
    @JvmField var bookShelfReturnPage: String? = null
    @JvmField var chaptersReturnPage: String? = null
    @JvmField var bookInfoReturnPage: String? = null
    @JvmField var collectionBooksReturnPage: String? = null
    @JvmField var bookShelfSearchQuery: String? = null
    @JvmField var settingsScrollY: Int = 0
    @JvmField val activeGenreFilter: MutableSet<String> = HashSet()
}

class BookJsonData {
    @JvmField var title: String = ""
    @JvmField var author: String = ""
    @JvmField var description: String = ""
    @JvmField var collection: String = ""
    @JvmField var coverUrl: String = ""
    @JvmField var chapterCount: Int = 0
    @JvmField var seriesNumber: Int = 0
    @JvmField var hasTitle: Boolean = false
    @JvmField var hasAuthor: Boolean = false
    @JvmField var hasDescription: Boolean = false
    @JvmField var hasGenres: Boolean = false
    @JvmField var hasCollection: Boolean = false
    @JvmField var hasChapterCount: Boolean = false
    @JvmField var hasSeriesNumber: Boolean = false
    @JvmField var hasCoverUrl: Boolean = false
    @JvmField val genres: ArrayList<String> = ArrayList()
}

class BookJsonImportTarget(
    @JvmField val title: EditText,
    @JvmField val author: EditText,
    @JvmField val count: EditText,
    @JvmField val seriesNumber: EditText,
    @JvmField val description: Array<String>,
    @JvmField val collectionId: Array<String>,
    @JvmField val selectedGenres: LinkedHashSet<String>,
    @JvmField val availableGenres: ArrayList<String>,
    @JvmField val descriptionEntry: View,
    @JvmField val genreEntry: View,
    @JvmField val refreshCollectionChoice: Runnable,
    @JvmField val showDetails: Runnable,
    @JvmField val isNew: Boolean,
    @JvmField val coverPreview: ImageView,
    @JvmField val coverRemoveButton: View,
    originalCoverName: String?,
) {
    @JvmField val originalCoverName: String = originalCoverName ?: ""
    @JvmField val createdCollectionIds: ArrayList<String> = ArrayList()
    @JvmField var saveButton: TextView? = null
}
