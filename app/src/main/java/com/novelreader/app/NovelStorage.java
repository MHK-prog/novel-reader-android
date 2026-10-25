package com.novelreader.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SAF storage for the existing Documents/NovelReader library format. */
final class NovelStorage {
    private static final String PREFS = "novel_storage";
    private static final String TREE_URI = "tree_uri";
    private static final String FOLDER_ID = "folder_document_id";
    private static final String FOLDER = "NovelReader";
    private static final String COVER_FOLDER = ".covers";
    private static final String NO_MEDIA_FILE = ".nomedia";
    private static final String DIRECTORY_MIME = "vnd.android.document/directory";
    private static final long MAX_COVER_CACHE_BYTES = 64L * 1024L * 1024L;

    private final MainActivity activity;
    private final SharedPreferences preferences;
    private final File legacyPrivateCoverDirectory;
    private final File coverCacheDirectory;
    private final Map<String, Uri> coverDocuments = new HashMap<>();
    private Uri cachedCoverRoot;
    private Uri cachedCoverDirectory;
    private boolean coverDirectoryResolved;
    private Uri indexedCoverDirectory;
    private boolean coverIndexLoaded;
    private String lastError = "";

    NovelStorage(MainActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        legacyPrivateCoverDirectory = new File(activity.getFilesDir(), "covers");
        coverCacheDirectory = new File(activity.getCacheDir(), "covers");
    }

    boolean isReady() {
        Uri dir = directoryUri();
        if (dir == null) return false;
        try { queryName(dir); return true; }
        catch (Exception error) {
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            android.util.Log.e("NovelReader", "Saved folder is unavailable", error);
            return false;
        }
    }

    void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        Uri documents = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Documents");
        intent.putExtra("android.provider.extra.INITIAL_URI", documents);
        activity.startActivityForResult(intent, MainActivity.REQUEST_FOLDER);
    }

    void acceptFolder(Uri selectedTree, int flags) throws Exception {
        int grant = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.getContentResolver().takePersistableUriPermission(selectedTree, grant);

        String selectedId = DocumentsContract.getTreeDocumentId(selectedTree);
        Uri selectedDoc = DocumentsContract.buildDocumentUriUsingTree(selectedTree, selectedId);
        String name = queryName(selectedDoc);
        String folderId;
        if (FOLDER.equalsIgnoreCase(name)) {
            folderId = selectedId;
        } else {
            Uri existing = findChild(selectedDoc, FOLDER);
            if (existing != null && !DIRECTORY_MIME.equals(activity.getContentResolver().getType(existing))) {
                throw new IllegalStateException("A file named NovelReader already exists.");
            }
            Uri folder = existing != null ? existing : DocumentsContract.createDocument(
                    activity.getContentResolver(), selectedDoc, DIRECTORY_MIME, FOLDER);
            if (folder == null) throw new IllegalStateException("Could not create the NovelReader folder.");
            folderId = DocumentsContract.getDocumentId(folder);
        }
        preferences.edit().putString(TREE_URI, selectedTree.toString()).putString(FOLDER_ID, folderId).apply();
        clearCoverDocumentCache();
    }

    String read(String fileName) throws Exception {
        Uri dir = directoryUri();
        if (dir == null) { lastError = "No NovelReader folder is selected."; throw new IOException(lastError); }
        if (!supportedName(fileName)) { lastError = "Only .md and .json files are supported."; throw new IOException(lastError); }
        try {
            Uri file = findChild(dir, fileName);
            if (file == null) { lastError = ""; return null; }
            try (InputStream in = activity.getContentResolver().openInputStream(file);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IOException("The storage provider returned no input stream.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
                lastError = "";
                return out.toString("UTF-8");
            }
        } catch (Exception error) {
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            android.util.Log.e("NovelReader", "Could not read " + fileName, error);
            throw error;
        }
    }

    boolean write(String fileName, String contents) {
        Uri dir = directoryUri();
        lastError = "";
        if (dir == null) { lastError = "No NovelReader folder is selected."; return false; }
        if (!supportedName(fileName)) { lastError = "Only .md and .json files are supported."; return false; }
        try {
            String mime = fileName.endsWith(".json") ? "application/json"
                    : "text/markdown";
            Uri file = findChild(dir, fileName);
            if (file == null) file = DocumentsContract.createDocument(
                    activity.getContentResolver(), dir, mime, fileName);
            if (file == null) throw new IOException("The storage provider could not create " + fileName + ".");
            try (OutputStream out = activity.getContentResolver().openOutputStream(file, "rwt")) {
                if (out == null) throw new IOException("The storage provider returned no output stream.");
                out.write((contents == null ? "" : contents).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return true;
        } catch (Exception error) {
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            android.util.Log.e("NovelReader", "Could not write " + fileName, error);
            return false;
        }
    }

    byte[] readImage(String fileName) throws Exception {
        if (!supportedImageName(fileName)) throw new IOException("Invalid cover image name.");
        byte[] cached = readCoverCache(fileName);
        if (cached != null) return cached;

        Uri coverDir = coverDirectoryUri(false);
        if (coverDir != null) {
            Uri cover = findCoverDocument(coverDir, fileName);
            if (cover != null) {
                byte[] contents = readDocumentBytes(cover, "The cover provider returned no image stream.");
                writeCoverCache(fileName, contents);
                return contents;
            }
        }

        byte[] legacyShared = readLegacyImage(fileName);
        if (legacyShared != null) {
            if (writeImage(fileName, legacyShared)) deleteLegacyImage(fileName);
            return legacyShared;
        }

        File legacyFile = legacyPrivateCoverFile(fileName);
        if (!legacyFile.isFile()) return null;
        try (InputStream in = new FileInputStream(legacyFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            byte[] contents = out.toByteArray();
            if (writeImage(fileName, contents) && !legacyFile.delete()) {
                android.util.Log.w("NovelReader", "Could not remove migrated cover " + fileName);
            }
            return contents;
        }
    }

    boolean writeImage(String fileName, byte[] contents) {
        if (!supportedImageName(fileName) || contents == null) return false;
        try {
            Uri coverDir = coverDirectoryUri(true);
            ensureMediaHidden(coverDir);
            Uri cover = findCoverDocument(coverDir, fileName);
            if (cover == null) {
                cover = DocumentsContract.createDocument(activity.getContentResolver(), coverDir, "image/jpeg", fileName);
                if (cover != null) {
                    coverDocuments.put(fileName, cover);
                    coverIndexLoaded = true;
                    indexedCoverDirectory = coverDir;
                }
            }
            if (cover == null) throw new IOException("The storage provider could not create the cover image.");
            try (OutputStream out = activity.getContentResolver().openOutputStream(cover, "rwt")) {
                if (out == null) throw new IOException("The storage provider returned no cover output stream.");
                out.write(contents);
                out.flush();
            }
            writeCoverCache(fileName, contents);
            lastError = "";
            return true;
        } catch (Exception error) {
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            android.util.Log.e("NovelReader", "Could not save cover", error);
            return false;
        }
    }

    void deleteImage(String fileName) {
        if (!supportedImageName(fileName)) return;
        File cachedFile = coverCacheFile(fileName);
        if (cachedFile.exists() && !cachedFile.delete()) {
            android.util.Log.w("NovelReader", "Could not remove cached cover " + fileName);
        }
        try {
            Uri coverDir = coverDirectoryUri(false);
            if (coverDir != null) {
                Uri cover = findCoverDocument(coverDir, fileName);
                if (cover != null && DocumentsContract.deleteDocument(activity.getContentResolver(), cover)) {
                    coverDocuments.remove(fileName);
                }
            }
            deleteLegacyImage(fileName);
            File oldFile = legacyPrivateCoverFile(fileName);
            if (oldFile.exists() && !oldFile.delete()) {
                android.util.Log.w("NovelReader", "Could not delete legacy cover " + fileName);
            }
        } catch (Exception error) {
            android.util.Log.w("NovelReader", "Could not delete " + fileName, error);
        }
    }

    /** Moves old covers into NovelReader/.covers and prevents gallery indexing with .nomedia. */
    void migrateLegacyImages() {
        Uri coverDir;
        try {
            coverDir = coverDirectoryUri(true);
            ensureMediaHidden(coverDir);
        } catch (Exception error) {
            android.util.Log.w("NovelReader", "Could not initialize hidden cover folder", error);
            return;
        }

        for (String fileName : listNames()) {
            if (!supportedImageName(fileName)) continue;
            try {
                byte[] contents = readLegacyImage(fileName);
                if (contents == null || !writeImage(fileName, contents)) continue;
                deleteLegacyImage(fileName);
            } catch (Exception error) {
                android.util.Log.w("NovelReader", "Could not migrate cover " + fileName, error);
            }
        }

        File[] oldFiles = legacyPrivateCoverDirectory.listFiles();
        if (oldFiles == null) return;
        for (File oldFile : oldFiles) {
            String fileName = oldFile.getName();
            if (!supportedImageName(fileName) || !oldFile.isFile()) continue;
            try {
                byte[] contents;
                try (InputStream in = new FileInputStream(oldFile);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    copy(in, out);
                    contents = out.toByteArray();
                }
                if (!writeImage(fileName, contents)) continue;
                if (!oldFile.delete()) android.util.Log.w("NovelReader", "Could not remove migrated cover " + fileName);
            } catch (Exception error) {
                android.util.Log.w("NovelReader", "Could not migrate private cover " + fileName, error);
            }
        }
    }

    private Uri coverDirectoryUri(boolean create) throws Exception {
        Uri root = directoryUri();
        if (root == null) {
            if (create) throw new IOException("No NovelReader folder is selected.");
            return null;
        }
        if (root.equals(cachedCoverRoot) && coverDirectoryResolved) {
            if (cachedCoverDirectory != null) return cachedCoverDirectory;
            if (!create) return null;
        } else {
            cachedCoverRoot = root;
            cachedCoverDirectory = null;
            coverDirectoryResolved = false;
            clearCoverIndex();
        }
        Uri covers = findChild(root, COVER_FOLDER);
        if (covers == null && create) {
            covers = DocumentsContract.createDocument(activity.getContentResolver(), root,
                    DIRECTORY_MIME, COVER_FOLDER);
        }
        cachedCoverRoot = root;
        cachedCoverDirectory = covers;
        coverDirectoryResolved = true;
        if (covers != null && !covers.equals(indexedCoverDirectory)) clearCoverIndex();
        if (covers == null) {
            if (create) throw new IOException("Could not create NovelReader/.covers.");
            return null;
        }
        String mime = activity.getContentResolver().getType(covers);
        if (mime != null && !DIRECTORY_MIME.equals(mime)) {
            throw new IOException("NovelReader/.covers is not a directory.");
        }
        return covers;
    }

    private void ensureMediaHidden(Uri coverDir) throws Exception {
        Uri marker = findCoverDocument(coverDir, NO_MEDIA_FILE);
        if (marker == null) marker = DocumentsContract.createDocument(
                activity.getContentResolver(), coverDir, "application/octet-stream", NO_MEDIA_FILE);
        if (marker == null) throw new IOException("Could not add the media exclusion marker.");
        coverDocuments.put(NO_MEDIA_FILE, marker);
        coverIndexLoaded = true;
        indexedCoverDirectory = coverDir;
    }

    private Uri findCoverDocument(Uri coverDir, String fileName) throws Exception {
        if (!coverDir.equals(indexedCoverDirectory)) clearCoverIndex();
        if (!coverIndexLoaded) {
            coverDocuments.clear();
            String id = DocumentsContract.getDocumentId(coverDir);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(coverDir, id);
            String[] columns = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME };
            try (Cursor cursor = activity.getContentResolver().query(children, columns, null, null, null)) {
                if (cursor == null) throw new IOException("The cover provider could not list the cover folder.");
                int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameColumn);
                    String documentId = cursor.getString(idColumn);
                    if (name != null && documentId != null) {
                        coverDocuments.put(name, DocumentsContract.buildDocumentUriUsingTree(coverDir, documentId));
                    }
                }
            }
            indexedCoverDirectory = coverDir;
            coverIndexLoaded = true;
        }
        return coverDocuments.get(fileName);
    }

    private void clearCoverDocumentCache() {
        cachedCoverRoot = null;
        cachedCoverDirectory = null;
        coverDirectoryResolved = false;
        clearCoverIndex();
    }

    private void clearCoverIndex() {
        coverDocuments.clear();
        indexedCoverDirectory = null;
        coverIndexLoaded = false;
    }

    private byte[] readCoverCache(String fileName) {
        File file = coverCacheFile(fileName);
        if (!file.isFile()) return null;
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output);
            file.setLastModified(System.currentTimeMillis());
            return output.toByteArray();
        } catch (IOException error) {
            android.util.Log.w("NovelReader", "Could not read cached cover " + fileName, error);
            if (!file.delete()) android.util.Log.w("NovelReader", "Could not remove invalid cover cache " + fileName);
            return null;
        }
    }

    private void writeCoverCache(String fileName, byte[] contents) {
        if (contents == null) return;
        File target = coverCacheFile(fileName);
        if (!coverCacheDirectory.isDirectory() && !coverCacheDirectory.mkdirs()) return;
        File temporary = new File(coverCacheDirectory, fileName + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(contents);
            output.flush();
            output.getFD().sync();
            if (!temporary.renameTo(target)) throw new IOException("Could not finalize cover cache file.");
            trimCoverCache(target);
        } catch (IOException error) {
            if (temporary.exists() && !temporary.delete()) {
                android.util.Log.w("NovelReader", "Could not remove temporary cover cache", error);
            }
            android.util.Log.w("NovelReader", "Could not cache cover " + fileName, error);
        }
    }

    private void trimCoverCache(File keep) {
        File[] files = coverCacheDirectory.listFiles((directory, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_COVER_CACHE_BYTES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_COVER_CACHE_BYTES) break;
            if (file.equals(keep)) continue;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private File coverCacheFile(String fileName) {
        return new File(coverCacheDirectory, fileName);
    }

    private byte[] readLegacyImage(String fileName) throws Exception {
        Uri dir = directoryUri();
        if (dir == null) return null;
        Uri file = findChild(dir, fileName);
        return file == null ? null : readDocumentBytes(file, "The storage provider returned no image stream.");
    }

    private byte[] readDocumentBytes(Uri file, String missingStreamMessage) throws IOException {
        try (InputStream in = activity.getContentResolver().openInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException(missingStreamMessage);
            copy(in, out);
            return out.toByteArray();
        }
    }

    private File legacyPrivateCoverFile(String fileName) throws IOException {
        if (!supportedImageName(fileName)) throw new IOException("Invalid cover image name.");
        return new File(legacyPrivateCoverDirectory, fileName);
    }

    private void deleteLegacyImage(String fileName) {
        Uri dir = directoryUri();
        if (dir == null) return;
        try {
            Uri file = findChild(dir, fileName);
            if (file != null) DocumentsContract.deleteDocument(activity.getContentResolver(), file);
        } catch (Exception error) {
            android.util.Log.w("NovelReader", "Could not remove old cover " + fileName, error);
        }
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
    }

    String lastError() { return lastError; }

    List<String> listNames() {
        List<String> names = new ArrayList<>();
        Uri dir = directoryUri();
        if (dir == null) return names;
        try {
            String id = DocumentsContract.getDocumentId(dir);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, id);
            String[] cols = { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
            try (Cursor cursor = activity.getContentResolver().query(children, cols, null, null, null)) {
                if (cursor != null) while (cursor.moveToNext()) names.add(cursor.getString(0));
            }
        } catch (Exception ignored) { }
        return names;
    }

    private Uri directoryUri() {
        String tree = preferences.getString(TREE_URI, null);
        String id = preferences.getString(FOLDER_ID, null);
        if (tree == null || id == null) return null;
        return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree), id);
    }

    private Uri findChild(Uri parent, String name) throws Exception {
        String id = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id);
        String[] cols = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor cursor = activity.getContentResolver().query(children, cols, null, null, null)) {
            if (cursor == null) throw new IOException("The storage provider could not list the selected folder.");
            int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            while (cursor.moveToNext()) if (name.equals(cursor.getString(nameCol))) {
                return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idCol));
            }
        }
        return null;
    }

    private String queryName(Uri document) throws Exception {
        String[] cols = { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor cursor = activity.getContentResolver().query(document, cols, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("Folder is unavailable.");
            return cursor.getString(0);
        }
    }

    private static boolean safeName(String name) {
        return name != null && name.length() <= 180 && !name.contains("..")
                && name.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private static boolean supportedName(String name) {
        return safeName(name) && (name.endsWith(".md") || name.endsWith(".json"));
    }

    private static boolean supportedImageName(String name) {
        return safeName(name) && name.startsWith("cover-") && name.endsWith(".jpg");
    }
}
