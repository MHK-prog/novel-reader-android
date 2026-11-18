package com.novelreader.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity(tableName = "library_items", primaryKeys = {"section", "itemId"},
        indices = {@Index(value = {"section"})})
final class LibraryRow {
    @NonNull public String section;
    @NonNull public String itemId;
    public int position;
    @NonNull public String payload;

    LibraryRow(String section, String itemId, int position, String payload) {
        this.section = section;
        this.itemId = itemId;
        this.position = position;
        this.payload = payload;
    }
}

@Dao
interface LibraryRowDao {
    @Query("SELECT * FROM library_items ORDER BY section, position")
    List<LibraryRow> readAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putAll(List<LibraryRow> rows);

    @Query("DELETE FROM library_items WHERE section = :section AND itemId = :itemId")
    void delete(String section, String itemId);
}

@Database(entities = {LibraryRow.class}, version = 1, exportSchema = false)
abstract class LibraryRoomDatabase extends RoomDatabase {
    abstract LibraryRowDao rows();
}

/** Keeps each library item in Room while the current UI continues to use its in-memory JSON model. */
final class RoomLibraryStore {
    private static final String DATABASE_NAME = "novel-library.db";
    private static final String ROOT_SECTION = "__root__";
    private static final String ROOT_ID = "root";
    private static final String ARRAY_MARKER_ID = "__array__";

    private final LibraryRoomDatabase database;
    private final LibraryRowDao dao;
    private Map<String, LibraryRow> cachedRows = new HashMap<>();

    RoomLibraryStore(Context context) {
        database = Room.databaseBuilder(context.getApplicationContext(),
                LibraryRoomDatabase.class, DATABASE_NAME)
                .allowMainThreadQueries()
                .build();
        dao = database.rows();
    }

    synchronized String read() throws JSONException {
        List<LibraryRow> rows = dao.readAll();
        if (rows.isEmpty()) return null;
        cachedRows = index(rows);

        JSONObject result = new JSONObject();
        Map<String, List<LibraryRow>> arrays = new HashMap<>();
        for (LibraryRow row : rows) {
            if (ROOT_SECTION.equals(row.section)) {
                if (ROOT_ID.equals(row.itemId)) result = new JSONObject(row.payload);
            } else {
                List<LibraryRow> section = arrays.get(row.section);
                if (section == null) {
                    section = new ArrayList<>();
                    arrays.put(row.section, section);
                }
                section.add(row);
            }
        }
        for (Map.Entry<String, List<LibraryRow>> entry : arrays.entrySet()) {
            JSONArray values = new JSONArray();
            List<LibraryRow> section = entry.getValue();
            for (LibraryRow row : section) {
                if (ARRAY_MARKER_ID.equals(row.itemId)) continue;
                Object value = new JSONTokener(row.payload).nextValue();
                values.put(value == JSONObject.NULL ? null : value);
            }
            result.put(entry.getKey(), values);
        }
        return result.toString();
    }

    synchronized void write(JSONObject library) throws JSONException {
        Map<String, LibraryRow> desired = flatten(library);
        List<LibraryRow> changed = new ArrayList<>();
        for (Map.Entry<String, LibraryRow> entry : desired.entrySet()) {
            LibraryRow previous = cachedRows.get(entry.getKey());
            LibraryRow current = entry.getValue();
            if (previous == null || previous.position != current.position
                    || !previous.payload.equals(current.payload)) changed.add(current);
        }
        Set<String> removedKeys = new HashSet<>(cachedRows.keySet());
        removedKeys.removeAll(desired.keySet());

        database.runInTransaction(() -> {
            for (String key : removedKeys) {
                LibraryRow old = cachedRows.get(key);
                if (old != null) dao.delete(old.section, old.itemId);
            }
            if (!changed.isEmpty()) dao.putAll(changed);
        });
        cachedRows = desired;
    }

    private Map<String, LibraryRow> flatten(JSONObject library) throws JSONException {
        Map<String, LibraryRow> rows = new HashMap<>();
        JSONObject root = new JSONObject();
        java.util.Iterator<String> keys = library.keys();
        while (keys.hasNext()) {
            String section = keys.next();
            Object value = library.opt(section);
            if (!(value instanceof JSONArray)) {
                root.put(section, value == null ? JSONObject.NULL : value);
                continue;
            }
            JSONArray array = (JSONArray) value;
            addRow(rows, new LibraryRow(section, ARRAY_MARKER_ID, -1, "[]"));
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                addRow(rows, new LibraryRow(section, Integer.toString(i), i,
                        encode(item)));
            }
        }
        addRow(rows, new LibraryRow(ROOT_SECTION, ROOT_ID, 0, root.toString()));
        return rows;
    }

    private String encode(Object item) throws JSONException {
        if (item == null || item == JSONObject.NULL) return "null";
        if (item instanceof JSONObject || item instanceof JSONArray) return item.toString();
        if (item instanceof String) return JSONObject.quote((String) item);
        return item.toString();
    }

    private void addRow(Map<String, LibraryRow> target, LibraryRow row) {
        target.put(key(row.section, row.itemId), row);
    }

    private Map<String, LibraryRow> index(List<LibraryRow> rows) {
        Map<String, LibraryRow> result = new HashMap<>();
        for (LibraryRow row : rows) result.put(key(row.section, row.itemId), row);
        return result;
    }

    private String key(String section, String itemId) {
        return section + '\u001f' + itemId;
    }
}
