package com.novelreader.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap

/** SAF storage for NovelReader documents. */
class NovelStorage(private val activity: MainActivity) {
    private val preferences: SharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val coverCacheDirectory = File(activity.cacheDir, "covers")
    private val coverDocuments: MutableMap<String, Uri> = HashMap()
    private var cachedCoverRoot: Uri? = null
    private var cachedCoverDirectory: Uri? = null
    private var coverDirectoryResolved = false
    private var indexedCoverDirectory: Uri? = null
    private var coverIndexLoaded = false
    private var lastErrorMessage = ""

    fun isReady(): Boolean {
        val directory = directoryUri() ?: return false
        return try {
            queryName(directory)
            true
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.e("NovelReader", "Saved folder is unavailable", error)
            false
        }
    }

    @Throws(Exception::class)
    fun readChapterFile(folderName: String, fileName: String): String? {
        if (!validFolderName(folderName) || !validChapterTextFileName(fileName)) {
            throw IOException("Invalid Chapter text path.")
        }
        val folder = chapterFolder(folderName, false) ?: return null
        val file = findChild(folder, fileName) ?: return null
        return readDocument(file)
    }

    fun writeChapterFile(folderName: String, fileName: String, contents: String?): Boolean {
        if (!validFolderName(folderName) || !validChapterTextFileName(fileName)) {
            lastErrorMessage = "Invalid Chapter text path."
            return false
        }
        return try {
            val folder = chapterFolder(folderName, true)
                ?: throw IOException("The book folder is unavailable.")
            val file = findChild(folder, fileName) ?: DocumentsContract.createDocument(
                activity.contentResolver, folder, "text/plain", fileName,
            ) ?: throw IOException("The storage provider could not create $fileName.")
            writeDocument(file, contents)
            lastErrorMessage = ""
            true
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.e("NovelReader", "Could not write Chapter $fileName", error)
            false
        }
    }

    fun chapterFileExists(folderName: String, fileName: String): Boolean = try {
        val folder = chapterFolder(folderName, false)
        folder != null && findChild(folder, fileName) != null
    } catch (_: Exception) {
        false
    }

    fun deleteChapterFile(folderName: String, fileName: String): Boolean {
        if (!validFolderName(folderName) || !validChapterTextFileName(fileName)) return false
        return try {
            val folder = chapterFolder(folderName, false)
            val file = if (folder == null) null else findChild(folder, fileName)
            file == null || DocumentsContract.deleteDocument(activity.contentResolver, file)
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            false
        }
    }

    @Throws(Exception::class)
    private fun chapterFolder(folderName: String, create: Boolean): Uri? {
        val root = directoryUri() ?: throw IOException("No NovelReader folder is selected.")
        var folder = findChild(root, folderName)
        if (folder == null && create) {
            folder = DocumentsContract.createDocument(activity.contentResolver, root, DIRECTORY_MIME, folderName)
        }
        if (folder == null) return null
        val type = activity.contentResolver.getType(folder)
        if (type != null && type != DIRECTORY_MIME) throw IOException("The book path is not a folder.")
        return folder
    }

    @Throws(Exception::class)
    private fun readDocument(document: Uri): String {
        activity.contentResolver.openInputStream(document).use { input ->
            ByteArrayOutputStream().use { output ->
                if (input == null) throw IOException("The storage provider returned no input stream.")
                copy(input, output)
                return output.toString("UTF-8")
            }
        }
    }

    @Throws(Exception::class)
    private fun writeDocument(document: Uri, content: String?) {
        activity.contentResolver.openOutputStream(document, "rwt").use { output ->
            if (output == null) throw IOException("The storage provider returned no output stream.")
            output.write((content ?: "").toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val documents = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Documents",
        )
        intent.putExtra("android.provider.extra.INITIAL_URI", documents)
        activity.startActivityForResult(intent, MainActivity.REQUEST_FOLDER)
    }

    @Throws(Exception::class)
    fun acceptFolder(selectedTree: Uri, flags: Int) {
        val grant = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        activity.contentResolver.takePersistableUriPermission(selectedTree, grant)

        val selectedId = DocumentsContract.getTreeDocumentId(selectedTree)
        val selectedDoc = DocumentsContract.buildDocumentUriUsingTree(selectedTree, selectedId)
        val name = queryName(selectedDoc)
        val folderId = if (name.equals(FOLDER, ignoreCase = true)) {
            selectedId
        } else {
            val existing = findChild(selectedDoc, FOLDER)
            if (existing != null && activity.contentResolver.getType(existing) != DIRECTORY_MIME) {
                throw IllegalStateException("A file named NovelReader already exists.")
            }
            val folder = existing ?: DocumentsContract.createDocument(
                activity.contentResolver, selectedDoc, DIRECTORY_MIME, FOLDER,
            ) ?: throw IllegalStateException("Could not create the NovelReader folder.")
            DocumentsContract.getDocumentId(folder)
        }
        preferences.edit()
            .putString(TREE_URI, selectedTree.toString())
            .putString(FOLDER_ID, folderId)
            .remove(PLUGIN_DOCUMENT_ID)
            .remove(PLUGIN_MARKER_DOCUMENT_ID)
            .apply()
        clearCoverDocumentCache()
    }

    @Throws(Exception::class)
    fun readPluginFile(pluginId: String, fileName: String): String? {
        if (!isPluginId(pluginId) || !safePluginFileName(fileName)) throw IOException("Invalid extension path.")
        val directory = pluginDirectoryUri(pluginId, false) ?: return null
        val file = findChild(directory, fileName) ?: return null
        activity.contentResolver.openInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                if (input == null) throw IOException("The extension provider returned no input stream.")
                copy(input, output)
                return output.toString("UTF-8")
            }
        }
    }

    fun writePluginFile(pluginId: String, fileName: String, contents: String?): Boolean {
        if (!isPluginId(pluginId) || !safePluginFileName(fileName)) {
            lastErrorMessage = "Invalid extension path."
            return false
        }
        return try {
            val directory = pluginDirectoryUri(pluginId, true)
                ?: throw IOException("The extension folder is unavailable.")
            val mime = if (fileName.endsWith(".json")) "application/json" else "application/octet-stream"
            val file = findChild(directory, fileName) ?: DocumentsContract.createDocument(
                activity.contentResolver, directory, mime, fileName,
            ) ?: throw IOException("The storage provider could not create $fileName.")
            activity.contentResolver.openOutputStream(file, "rwt").use { output ->
                if (output == null) throw IOException("The storage provider returned no extension output stream.")
                output.write((contents ?: "").toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
            lastErrorMessage = ""
            true
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.e("NovelReader", "Could not write extension file $fileName", error)
            false
        }
    }

    fun keepColloquializerRulesOnly(): Boolean {
        val directory = pluginDirectoryUri("colloquializer", false) ?: return true
        return try {
            val documentId = DocumentsContract.getDocumentId(directory)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(directory, documentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val extras = ArrayList<Uri>()
            activity.contentResolver.query(children, columns, null, null, null).use { cursor ->
                if (cursor != null) {
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameColumn) == "rules.json") continue
                        if (cursor.getString(typeColumn) != DIRECTORY_MIME) {
                            extras.add(DocumentsContract.buildDocumentUriUsingTree(
                                directory, cursor.getString(idColumn),
                            ))
                        }
                    }
                }
            }
            for (file in extras) {
                if (!DocumentsContract.deleteDocument(activity.contentResolver, file)) {
                    throw IOException("Could not remove an old colloquializer file.")
                }
            }
            true
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.w("NovelReader", "Could not remove old colloquializer files", error)
            false
        }
    }

    @Throws(Exception::class)
    fun readChapterOriginal(fileName: String): String? {
        if (!safePluginFileName(fileName) || !fileName.endsWith(".txt")) {
            throw IOException("Invalid chapter original file name.")
        }
        val directory = chapterOriginalsDirectory(false) ?: return null
        val file = findChild(directory, fileName) ?: return null
        activity.contentResolver.openInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                if (input == null) throw IOException("The chapter original is unavailable.")
                copy(input, output)
                return output.toString("UTF-8")
            }
        }
    }

    fun writeChapterOriginal(fileName: String, contents: String?): Boolean {
        if (!safePluginFileName(fileName) || !fileName.endsWith(".txt")) {
            lastErrorMessage = "Invalid chapter original file name."
            return false
        }
        return try {
            val directory = chapterOriginalsDirectory(true)
                ?: throw IOException("The chapter originals folder is unavailable.")
            val file = findChild(directory, fileName) ?: DocumentsContract.createDocument(
                activity.contentResolver, directory, "text/plain", fileName,
            ) ?: throw IOException("Could not create the chapter original.")
            activity.contentResolver.openOutputStream(file, "rwt").use { output ->
                if (output == null) throw IOException("The chapter original is unavailable.")
                output.write((contents ?: "").toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
            lastErrorMessage = ""
            true
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.e("NovelReader", "Could not save chapter original", error)
            false
        }
    }

    fun deleteChapterOriginal(fileName: String): Boolean {
        if (!safePluginFileName(fileName) || !fileName.endsWith(".txt")) return false
        return try {
            val directory = chapterOriginalsDirectory(false) ?: return true
            val file = findChild(directory, fileName)
            file == null || DocumentsContract.deleteDocument(activity.contentResolver, file)
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.e("NovelReader", "Could not remove chapter original", error)
            false
        }
    }

    @Throws(Exception::class)
    private fun chapterOriginalsDirectory(create: Boolean): Uri? {
        val root = extensionRootUri(create) ?: return null
        return resolveExtensionChild(
            root, CHAPTER_ORIGINALS_FOLDER, CHAPTER_ORIGINALS_DOCUMENT_ID,
            DIRECTORY_MIME, true, create,
        )
    }

    @Throws(Exception::class)
    private fun extensionRootUri(create: Boolean): Uri? {
        val root = directoryUri()
        if (root == null) {
            if (create) throw IOException("No NovelReader folder is selected.")
            return null
        }
        val plugins = resolveExtensionChild(root, PLUGIN_FOLDER, PLUGIN_DOCUMENT_ID, DIRECTORY_MIME, true, create)
            ?: return null
        if (create && resolveExtensionChild(
                plugins, NO_MEDIA_FILE, PLUGIN_MARKER_DOCUMENT_ID, "application/octet-stream", false, true,
            ) == null
        ) {
            throw IOException("Could not hide the extension storage folder from media scanners.")
        }
        return plugins
    }

    @Throws(Exception::class)
    private fun pluginDirectoryUri(pluginId: String, create: Boolean): Uri? {
        if (!isPluginId(pluginId)) throw IOException("Invalid extension id.")
        val root = extensionRootUri(create) ?: return null
        return resolveExtensionChild(root, pluginId, pluginPreferenceKey(pluginId), DIRECTORY_MIME, true, create)
    }

    @Throws(Exception::class)
    private fun resolveExtensionChild(
        parent: Uri,
        name: String,
        preferenceKey: String,
        mimeType: String,
        directory: Boolean,
        create: Boolean,
    ): Uri? {
        val rememberedId = preferences.getString(preferenceKey, null)
        if (rememberedId != null) {
            val tree = Uri.parse(preferences.getString(TREE_URI, "") ?: "")
            val remembered = DocumentsContract.buildDocumentUriUsingTree(tree, rememberedId)
            if (extensionDocumentExists(remembered, directory)) return remembered
            preferences.edit().remove(preferenceKey).apply()
        }

        val direct = directExternalStorageChild(parent, name)
        if (direct != null && extensionDocumentExists(direct, directory)) {
            rememberExtensionDocument(preferenceKey, direct)
            return direct
        }

        val existing = findChild(parent, name)
        if (existing != null && extensionDocumentExists(existing, directory)) {
            rememberExtensionDocument(preferenceKey, existing)
            return existing
        }
        if (!create) return null

        val created = if (directory) createDirectory(parent, name)
        else DocumentsContract.createDocument(activity.contentResolver, parent, mimeType, name)
        if (created == null) throw IOException("Could not create $name.")
        rememberExtensionDocument(preferenceKey, created)
        return created
    }

    @Throws(Exception::class)
    private fun directExternalStorageChild(parent: Uri, name: String): Uri? {
        if (parent.authority != "com.android.externalstorage.documents") return null
        val parentId = DocumentsContract.getDocumentId(parent)
        return DocumentsContract.buildDocumentUriUsingTree(parent, "$parentId/$name")
    }

    private fun extensionDocumentExists(document: Uri, directory: Boolean): Boolean = try {
        queryName(document)
        val mime = activity.contentResolver.getType(document)
        !directory || mime == null || mime == DIRECTORY_MIME
    } catch (_: Exception) {
        false
    }

    @Throws(Exception::class)
    private fun rememberExtensionDocument(preferenceKey: String, document: Uri) {
        preferences.edit().putString(preferenceKey, DocumentsContract.getDocumentId(document)).apply()
    }

    @Throws(Exception::class)
    private fun createDirectory(parent: Uri, name: String): Uri = DocumentsContract.createDocument(
        activity.contentResolver, parent, DIRECTORY_MIME, name,
    ) ?: throw IOException("Could not create $name.")

    @Throws(Exception::class)
    fun readImage(fileName: String): ByteArray? {
        if (!supportedImageName(fileName)) throw IOException("Invalid cover image name.")
        readCoverCache(fileName)?.let { return it }
        val coverDirectory = coverDirectoryUri(false)
        if (coverDirectory != null) {
            val cover = findCoverDocument(coverDirectory, fileName)
            if (cover != null) {
                val contents = readDocumentBytes(cover, "The cover provider returned no image stream.")
                writeCoverCache(fileName, contents)
                return contents
            }
        }
        return null
    }

    fun writeImage(fileName: String, contents: ByteArray?): Boolean {
        if (!supportedImageName(fileName) || contents == null) return false
        return try {
            val coverDirectory = coverDirectoryUri(true)
                ?: throw IOException("The cover folder is unavailable.")
            ensureMediaHidden(coverDirectory)
            val cover = findCoverDocument(coverDirectory, fileName) ?: DocumentsContract.createDocument(
                activity.contentResolver, coverDirectory, "image/jpeg", fileName,
            )?.also {
                coverDocuments[fileName] = it
                coverIndexLoaded = true
                indexedCoverDirectory = coverDirectory
            } ?: throw IOException("The storage provider could not create the cover image.")
            activity.contentResolver.openOutputStream(cover, "rwt").use { output ->
                if (output == null) throw IOException("The storage provider returned no cover output stream.")
                output.write(contents)
                output.flush()
            }
            writeCoverCache(fileName, contents)
            lastErrorMessage = ""
            true
        } catch (error: Exception) {
            lastErrorMessage = error.message ?: error.javaClass.simpleName
            android.util.Log.e("NovelReader", "Could not save cover", error)
            false
        }
    }

    fun deleteImage(fileName: String) {
        if (!supportedImageName(fileName)) return
        val cachedFile = coverCacheFile(fileName)
        if (cachedFile.exists() && !cachedFile.delete()) {
            android.util.Log.w("NovelReader", "Could not remove cached cover $fileName")
        }
        try {
            val coverDirectory = coverDirectoryUri(false)
            if (coverDirectory != null) {
                val cover = findCoverDocument(coverDirectory, fileName)
                if (cover != null && DocumentsContract.deleteDocument(activity.contentResolver, cover)) {
                    coverDocuments.remove(fileName)
                }
            }
        } catch (error: Exception) {
            android.util.Log.w("NovelReader", "Could not delete $fileName", error)
        }
    }

    @Throws(Exception::class)
    private fun coverDirectoryUri(create: Boolean): Uri? {
        val root = directoryUri()
        if (root == null) {
            if (create) throw IOException("No NovelReader folder is selected.")
            return null
        }
        if (root == cachedCoverRoot && coverDirectoryResolved) {
            cachedCoverDirectory?.let { return it }
            if (!create) return null
        } else {
            cachedCoverRoot = root
            cachedCoverDirectory = null
            coverDirectoryResolved = false
            clearCoverIndex()
        }
        var covers = findChild(root, COVER_FOLDER)
        if (covers == null && create) {
            covers = DocumentsContract.createDocument(activity.contentResolver, root, DIRECTORY_MIME, COVER_FOLDER)
        }
        cachedCoverRoot = root
        cachedCoverDirectory = covers
        coverDirectoryResolved = true
        if (covers != indexedCoverDirectory) clearCoverIndex()
        if (covers == null) {
            if (create) throw IOException("Could not create NovelReader/.covers.")
            return null
        }
        val mime = activity.contentResolver.getType(covers)
        if (mime != null && mime != DIRECTORY_MIME) throw IOException("NovelReader/.covers is not a directory.")
        return covers
    }

    @Throws(Exception::class)
    private fun ensureMediaHidden(coverDirectory: Uri) {
        val marker = findCoverDocument(coverDirectory, NO_MEDIA_FILE) ?: DocumentsContract.createDocument(
            activity.contentResolver, coverDirectory, "application/octet-stream", NO_MEDIA_FILE,
        ) ?: throw IOException("Could not add the media exclusion marker.")
        coverDocuments[NO_MEDIA_FILE] = marker
        coverIndexLoaded = true
        indexedCoverDirectory = coverDirectory
    }

    @Throws(Exception::class)
    private fun findCoverDocument(coverDirectory: Uri, fileName: String): Uri? {
        if (coverDirectory != indexedCoverDirectory) clearCoverIndex()
        if (!coverIndexLoaded) {
            coverDocuments.clear()
            val id = DocumentsContract.getDocumentId(coverDirectory)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(coverDirectory, id)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            activity.contentResolver.query(children, columns, null, null, null).use { cursor ->
                if (cursor == null) throw IOException("The cover provider could not list the cover folder.")
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    val documentId = cursor.getString(idColumn)
                    if (name != null && documentId != null) {
                        coverDocuments[name] = DocumentsContract.buildDocumentUriUsingTree(coverDirectory, documentId)
                    }
                }
            }
            indexedCoverDirectory = coverDirectory
            coverIndexLoaded = true
        }
        return coverDocuments[fileName]
    }

    private fun clearCoverDocumentCache() {
        cachedCoverRoot = null
        cachedCoverDirectory = null
        coverDirectoryResolved = false
        clearCoverIndex()
    }

    private fun clearCoverIndex() {
        coverDocuments.clear()
        indexedCoverDirectory = null
        coverIndexLoaded = false
    }

    private fun readCoverCache(fileName: String): ByteArray? {
        val file = coverCacheFile(fileName)
        if (!file.isFile) return null
        return try {
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { output ->
                    copy(input, output)
                    file.setLastModified(System.currentTimeMillis())
                    output.toByteArray()
                }
            }
        } catch (error: IOException) {
            android.util.Log.w("NovelReader", "Could not read cached cover $fileName", error)
            if (!file.delete()) android.util.Log.w("NovelReader", "Could not remove invalid cover cache $fileName")
            null
        }
    }

    private fun writeCoverCache(fileName: String, contents: ByteArray?) {
        if (contents == null) return
        val target = coverCacheFile(fileName)
        if (!coverCacheDirectory.isDirectory && !coverCacheDirectory.mkdirs()) return
        val temporary = File(coverCacheDirectory, "$fileName.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(contents)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) throw IOException("Could not finalize cover cache file.")
            trimCoverCache(target)
        } catch (error: IOException) {
            if (temporary.exists() && !temporary.delete()) {
                android.util.Log.w("NovelReader", "Could not remove temporary cover cache", error)
            }
            android.util.Log.w("NovelReader", "Could not cache cover $fileName", error)
        }
    }

    private fun trimCoverCache(keep: File) {
        val files = coverCacheDirectory.listFiles { _, name -> name.endsWith(".jpg") } ?: return
        var total = files.sumOf(File::length)
        if (total <= MAX_COVER_CACHE_BYTES) return
        files.sortBy { it.lastModified() }
        for (file in files) {
            if (total <= MAX_COVER_CACHE_BYTES) break
            if (file == keep) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun coverCacheFile(fileName: String) = File(coverCacheDirectory, fileName)

    @Throws(IOException::class)
    private fun readDocumentBytes(file: Uri, missingStreamMessage: String): ByteArray {
        activity.contentResolver.openInputStream(file).use { input ->
            ByteArrayOutputStream().use { output ->
                if (input == null) throw IOException(missingStreamMessage)
                copy(input, output)
                return output.toByteArray()
            }
        }
    }

    fun lastError(): String = lastErrorMessage

    private fun directoryUri(): Uri? {
        val tree = preferences.getString(TREE_URI, null)
        val id = preferences.getString(FOLDER_ID, null)
        if (tree == null || id == null) return null
        return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree), id)
    }

    @Throws(Exception::class)
    private fun findChild(parent: Uri, name: String): Uri? {
        val id = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        activity.contentResolver.query(children, columns, null, null, null).use { cursor ->
            if (cursor == null) throw IOException("The storage provider could not list the selected folder.")
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (name == cursor.getString(nameColumn)) {
                    return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idColumn))
                }
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun queryName(document: Uri): String {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        activity.contentResolver.query(document, columns, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) throw IllegalStateException("Folder is unavailable.")
            return cursor.getString(0)
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    companion object {
        private const val PREFS = "novel_storage"
        private const val TREE_URI = "tree_uri"
        private const val FOLDER_ID = "folder_document_id"
        private const val FOLDER = "NovelReader"
        private const val COVER_FOLDER = ".covers"
        private const val PLUGIN_FOLDER = ".plugins"
        private const val CHAPTER_ORIGINALS_FOLDER = ".chapter-originals"
        private const val CHAPTER_ORIGINALS_DOCUMENT_ID = "chapter_originals_document_id"
        private const val PLUGIN_DOCUMENT_ID = "plugin_folder_document_id"
        private const val PLUGIN_MARKER_DOCUMENT_ID = "plugin_marker_document_id"
        private const val NO_MEDIA_FILE = ".nomedia"
        private const val DIRECTORY_MIME = "vnd.android.document/directory"
        private const val MAX_COVER_CACHE_BYTES = 64L * 1024L * 1024L

        private fun validFolderName(name: String?): Boolean =
            !name.isNullOrEmpty() && name.length <= 120 && name != "." && name != ".." &&
                !name.contains('/') && !name.contains('\\') && !name.contains("..") && !name.contains('\u0000')

        private fun validChapterTextFileName(name: String?): Boolean =
            name != null && name.length <= 140 && name.endsWith(".txt") && !name.startsWith(".") &&
                !name.contains('/') && !name.contains('\\') && !name.contains("..") && !name.contains('\u0000')

        private fun isPluginId(id: String?): Boolean =
            id != null && id.length <= 64 && id.matches(Regex("[a-z0-9][a-z0-9._-]*")) && !id.contains("..")

        private fun safePluginFileName(name: String?): Boolean =
            name != null && name.length <= 100 && !name.contains("..") &&
                name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))

        private fun pluginPreferenceKey(pluginId: String) = "plugin_directory_$pluginId"

        private fun safeName(name: String?): Boolean =
            name != null && name.length <= 180 && !name.contains("..") &&
                name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))

        private fun supportedImageName(name: String?): Boolean =
            safeName(name) && name!!.startsWith("cover-") && name.endsWith(".jpg")
    }
}
