package com.novelreader.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

@Entity(
    tableName = "library_items",
    primaryKeys = ["section", "itemId"],
    indices = [Index(value = ["section"])],
)
data class LibraryRow(
    var section: String,
    var itemId: String,
    var position: Int,
    var payload: String,
)

@Dao
interface LibraryRowDao {
    @Query("SELECT * FROM library_items ORDER BY section, position")
    fun readAll(): List<LibraryRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(rows: List<LibraryRow>)

    @Query("DELETE FROM library_items WHERE section = :section AND itemId = :itemId")
    fun delete(section: String, itemId: String)
}

@Database(entities = [LibraryRow::class], version = 1, exportSchema = false)
abstract class LibraryRoomDatabase : RoomDatabase() {
    abstract fun rows(): LibraryRowDao
}

/** Keeps each library item in Room while the current UI continues to use its in-memory JSON model. */
class RoomLibraryStore(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        LibraryRoomDatabase::class.java,
        DATABASE_NAME,
    ).allowMainThreadQueries().build()
    private val dao = database.rows()
    private var cachedRows: Map<String, LibraryRow> = HashMap()

    @Synchronized
    @Throws(JSONException::class)
    fun read(): String? {
        val rows = dao.readAll()
        if (rows.isEmpty()) return null
        cachedRows = index(rows)

        var result = JSONObject()
        val arrays = HashMap<String, MutableList<LibraryRow>>()
        for (row in rows) {
            if (row.section == ROOT_SECTION) {
                if (row.itemId == ROOT_ID) result = JSONObject(row.payload)
            } else {
                arrays.getOrPut(row.section) { ArrayList() }.add(row)
            }
        }
        for ((section, sectionRows) in arrays) {
            val values = JSONArray()
            for (row in sectionRows) {
                if (row.itemId == ARRAY_MARKER_ID) continue
                val value = JSONTokener(row.payload).nextValue()
                values.put(if (value == JSONObject.NULL) null else value)
            }
            result.put(section, values)
        }
        return result.toString()
    }

    @Synchronized
    @Throws(JSONException::class)
    fun write(library: JSONObject) {
        val desired = flatten(library)
        val changed = ArrayList<LibraryRow>()
        for ((key, current) in desired) {
            val previous = cachedRows[key]
            if (previous == null || previous.position != current.position || previous.payload != current.payload) {
                changed.add(current)
            }
        }
        val removedKeys = HashSet(cachedRows.keys)
        removedKeys.removeAll(desired.keys)

        database.runInTransaction(Runnable {
            for (key in removedKeys) {
                cachedRows[key]?.let { dao.delete(it.section, it.itemId) }
            }
            if (changed.isNotEmpty()) dao.putAll(changed)
        })
        cachedRows = desired
    }

    @Throws(JSONException::class)
    private fun flatten(library: JSONObject): Map<String, LibraryRow> {
        val rows = HashMap<String, LibraryRow>()
        val root = JSONObject()
        val keys = library.keys()
        while (keys.hasNext()) {
            val section = keys.next()
            val value = library.opt(section)
            if (value !is JSONArray) {
                root.put(section, value ?: JSONObject.NULL)
                continue
            }
            addRow(rows, LibraryRow(section, ARRAY_MARKER_ID, -1, "[]"))
            for (i in 0 until value.length()) {
                addRow(rows, LibraryRow(section, i.toString(), i, encode(value.opt(i))))
            }
        }
        addRow(rows, LibraryRow(ROOT_SECTION, ROOT_ID, 0, root.toString()))
        return rows
    }

    @Throws(JSONException::class)
    private fun encode(item: Any?): String {
        if (item == null || item == JSONObject.NULL) return "null"
        if (item is JSONObject || item is JSONArray) return item.toString()
        if (item is String) return JSONObject.quote(item)
        return item.toString()
    }

    private fun addRow(target: MutableMap<String, LibraryRow>, row: LibraryRow) {
        target[key(row.section, row.itemId)] = row
    }

    private fun index(rows: List<LibraryRow>): Map<String, LibraryRow> =
        rows.associateBy { key(it.section, it.itemId) }

    private fun key(section: String, itemId: String): String = "$section\u001f$itemId"

    companion object {
        private const val DATABASE_NAME = "novel-library.db"
        private const val ROOT_SECTION = "__root__"
        private const val ROOT_ID = "root"
        private const val ARRAY_MARKER_ID = "__array__"
    }
}
