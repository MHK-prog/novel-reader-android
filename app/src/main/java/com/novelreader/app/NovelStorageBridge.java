package com.novelreader.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Stores the web app's library and chapter text in the user-selected Documents folder. */
public final class NovelStorageBridge {
    private static final String PREFS = "novel_storage";
    private static final String PREF_TREE_URI = "tree_uri";
    private static final String PREF_FOLDER_ID = "folder_document_id";
    private static final String FOLDER_NAME = "NovelReader";
    private static final String DIRECTORY_MIME = "vnd.android.document/directory";

    private final MainActivity activity;
    private final ContentResolver resolver;
    private final SharedPreferences preferences;
    private boolean pickerOpen;

    NovelStorageBridge(MainActivity activity) {
        this.activity = activity;
        this.resolver = activity.getContentResolver();
        this.preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Opens the Android folder picker the first time; later calls reuse its saved grant. */
    @JavascriptInterface
    public synchronized String initDirectory() {
        if (hasUsableDirectory()) return "{\"status\":\"ready\"}";
        if (!pickerOpen) {
            pickerOpen = true;
            Uri documents = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Documents");
            activity.requestNovelFolder(documents);
        }
        return "{\"status\":\"pending\"}";
    }

    /** Saves UTF-8 text in NovelReader and returns "true" only after the write succeeds. */
    @JavascriptInterface
    public synchronized String saveNovel(String fileName, String content) {
        Uri directory = getDirectoryUri();
        if (directory == null || !isSafeFileName(fileName)) return "false";
        try {
            Uri file = findChild(directory, fileName);
            String mime = fileName.endsWith(".json") ? "application/json" : "text/plain";
            if (file == null) file = DocumentsContract.createDocument(resolver, directory, mime, fileName);
            if (file == null) return "false";
            try (OutputStream output = resolver.openOutputStream(file, "wt")) {
                if (output == null) return "false";
                output.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            return "true";
        } catch (Exception error) {
            return "false";
        }
    }

    /** Returns file contents as UTF-8 text, or an empty string when the file is absent. */
    @JavascriptInterface
    public synchronized String loadNovel(String fileName) {
        Uri directory = getDirectoryUri();
        if (directory == null || !isSafeFileName(fileName)) return "";
        try {
            Uri file = findChild(directory, fileName);
            if (file == null) return "";
            try (InputStream input = resolver.openInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) return "";
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return output.toString("UTF-8");
            }
        } catch (Exception error) {
            return "";
        }
    }

    /** Lists names of files directly inside NovelReader as a JSON array. */
    @JavascriptInterface
    public synchronized String getNovelList() {
        JSONArray names = new JSONArray();
        Uri directory = getDirectoryUri();
        if (directory == null) return names.toString();
        try {
            for (String name : listChildren(directory).names) names.put(name);
        } catch (Exception ignored) {
            // Return the successfully collected (possibly empty) list.
        }
        return names.toString();
    }

    synchronized void onFolderChosen(Uri selectedTree, int resultFlags) {
        pickerOpen = false;
        try {
            int accessFlags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                activity.getContentResolver().takePersistableUriPermission(selectedTree, accessFlags);
            }

            String selectedId = DocumentsContract.getTreeDocumentId(selectedTree);
            Uri selectedDocument = DocumentsContract.buildDocumentUriUsingTree(selectedTree, selectedId);
            String selectedName = queryDisplayName(selectedDocument);
            String folderId;
            if (FOLDER_NAME.equalsIgnoreCase(selectedName)) {
                folderId = selectedId;
            } else {
                Uri existing = findChild(selectedDocument, FOLDER_NAME);
                if (existing != null && !DIRECTORY_MIME.equals(resolver.getType(existing))) {
                    throw new IllegalStateException("A non-folder named NovelReader already exists");
                }
                Uri folder = existing != null ? existing : DocumentsContract.createDocument(
                        resolver, selectedDocument, DIRECTORY_MIME, FOLDER_NAME);
                if (folder == null) throw new IllegalStateException("Could not create NovelReader folder");
                folderId = DocumentsContract.getDocumentId(folder);
            }

            preferences.edit()
                    .putString(PREF_TREE_URI, selectedTree.toString())
                    .putString(PREF_FOLDER_ID, folderId)
                    .apply();
            activity.notifyWebStorageReady(true);
        } catch (Exception error) {
            activity.notifyWebStorageReady(false);
        }
    }

    synchronized void notifyWebStorageReady(boolean ready) {
        pickerOpen = false;
        activity.notifyWebStorageReady(ready);
    }

    private boolean hasUsableDirectory() {
        Uri directory = getDirectoryUri();
        if (directory == null) return false;
        try {
            queryDisplayName(directory);
            return true;
        } catch (Exception error) {
            preferences.edit().remove(PREF_TREE_URI).remove(PREF_FOLDER_ID).apply();
            return false;
        }
    }

    private Uri getDirectoryUri() {
        String tree = preferences.getString(PREF_TREE_URI, null);
        String documentId = preferences.getString(PREF_FOLDER_ID, null);
        if (tree == null || documentId == null) return null;
        Uri treeUri = Uri.parse(tree);
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
    }

    private Uri findChild(Uri directory, String name) throws Exception {
        for (DocumentEntry entry : listChildren(directory).entries) {
            if (name.equals(entry.name)) return entry.uri;
        }
        return null;
    }

    private DocumentListing listChildren(Uri directory) throws Exception {
        String documentId = DocumentsContract.getDocumentId(directory);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directory, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        List<DocumentEntry> entries = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return new DocumentListing(entries, names);
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            while (cursor.moveToNext()) {
                String childId = cursor.getString(idColumn);
                String childName = cursor.getString(nameColumn);
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(directory, childId);
                entries.add(new DocumentEntry(childName, childUri));
                names.add(childName);
            }
        }
        return new DocumentListing(entries, names);
    }

    private String queryDisplayName(Uri document) throws Exception {
        String[] projection = { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor cursor = resolver.query(document, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("Folder unavailable");
            return cursor.getString(0);
        }
    }

    private boolean isSafeFileName(String fileName) {
        return fileName != null
                && fileName.length() <= 160
                && !fileName.contains("..")
                && fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private static final class DocumentEntry {
        final String name;
        final Uri uri;
        DocumentEntry(String name, Uri uri) { this.name = name; this.uri = uri; }
    }

    private static final class DocumentListing {
        final List<DocumentEntry> entries;
        final List<String> names;
        DocumentListing(List<DocumentEntry> entries, List<String> names) {
            this.entries = entries;
            this.names = names;
        }
    }
}
