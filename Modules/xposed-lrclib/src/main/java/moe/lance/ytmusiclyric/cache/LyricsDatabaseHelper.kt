package moe.lance.ytmusiclyric.cache

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.abs

class LyricsDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CACHE_KEY TEXT UNIQUE NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_RAW_LRC TEXT NOT NULL,
                $COL_SOURCE TEXT NOT NULL,
                $COL_DURATION_MS INTEGER DEFAULT 0,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_lyrics_cache_key ON $TABLE_NAME($COL_CACHE_KEY)")
        db.execSQL("CREATE INDEX idx_lyrics_updated_at ON $TABLE_NAME($COL_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX idx_lyrics_title_artist ON $TABLE_NAME($COL_TITLE, $COL_ARTIST)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations if needed
    }

    @Synchronized
    fun get(cacheKey: String): LyricsCacheEntry? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COL_CACHE_KEY = ?",
            arrayOf(cacheKey),
            null,
            null,
            null,
            "1",
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToEntry(it) else null
        }
    }

    @Synchronized
    fun findBestMatch(
        cacheKey: String,
        cleanTitle: String,
        cleanArtist: String,
        durationMs: Long,
    ): LyricsCacheEntry? {
        // 1. Direct hit by exact cache key
        val exact = get(cacheKey)
        if (exact?.hasLyrics == true) return exact

        // 2. Fallback: match by title and artist, checking duration tolerance if durationMs > 0
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COL_TITLE = ? AND $COL_ARTIST = ? AND TRIM($COL_RAW_LRC) != ''",
            arrayOf(cleanTitle, cleanArtist),
            null,
            null,
            "$COL_UPDATED_AT DESC",
            "10",
        )

        var closest: LyricsCacheEntry? = null
        var minDiff = Long.MAX_VALUE

        cursor.use {
            while (it.moveToNext()) {
                val entry = cursorToEntry(it)
                if (!entry.hasLyrics) continue
                if (durationMs <= 0L || entry.durationMs <= 0L) {
                    return entry
                }
                val diff = abs(entry.durationMs - durationMs)
                if (diff <= 15_000L && diff < minDiff) {
                    minDiff = diff
                    closest = entry
                }
            }
        }
        return closest ?: exact
    }

    @Synchronized
    fun insertOrUpdate(entry: LyricsCacheEntry): Boolean {
        val db = writableDatabase
        // A late failed request must never erase lyrics saved by another process.
        // IGNORE also keeps repeated failures as a single, stable song record.
        val values = ContentValues().apply {
            put(COL_CACHE_KEY, entry.cacheKey)
            put(COL_TITLE, entry.title)
            put(COL_ARTIST, entry.artist)
            put(COL_RAW_LRC, entry.rawLrc)
            put(COL_SOURCE, entry.source)
            put(COL_DURATION_MS, entry.durationMs)
            put(COL_UPDATED_AT, entry.updatedAt)
        }
        val conflict = if (entry.hasLyrics) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE
        val id = db.insertWithOnConflict(TABLE_NAME, null, values, conflict)
        return id != -1L
    }

    @Synchronized
    fun updateLrc(cacheKey: String, rawLrc: String, source: String = "自定义编辑"): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_RAW_LRC, rawLrc)
            put(COL_SOURCE, source)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        val rows = db.update(TABLE_NAME, values, "$COL_CACHE_KEY = ?", arrayOf(cacheKey))
        return rows > 0
    }

    @Synchronized
    fun delete(cacheKey: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_NAME, "$COL_CACHE_KEY = ?", arrayOf(cacheKey))
        return rows > 0
    }

    @Synchronized
    fun deleteById(id: Long): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
        return rows > 0
    }

    @Synchronized
    fun deleteAll(): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, null, null)
    }

    @Synchronized
    fun getAll(searchKeyword: String? = null, limit: Int = 500, offset: Int = 0): List<LyricsCacheEntry> {
        val db = readableDatabase
        val (selection, selectionArgs) = if (!searchKeyword.isNullOrBlank()) {
            val kw = "%${searchKeyword.trim()}%"
            Pair("$COL_TITLE LIKE ? OR $COL_ARTIST LIKE ?", arrayOf(kw, kw))
        } else {
            Pair(null, null)
        }

        val cursor = db.query(
            TABLE_NAME,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COL_UPDATED_AT DESC",
            "$offset, $limit",
        )

        return cursor.use {
            val list = ArrayList<LyricsCacheEntry>(it.count)
            while (it.moveToNext()) {
                list.add(cursorToEntry(it))
            }
            list
        }
    }

    @Synchronized
    fun getCount(searchKeyword: String? = null): Int {
        val db = readableDatabase
        val (selection, selectionArgs) = if (!searchKeyword.isNullOrBlank()) {
            val kw = "%${searchKeyword.trim()}%"
            Pair("$COL_TITLE LIKE ? OR $COL_ARTIST LIKE ?", arrayOf(kw, kw))
        } else {
            Pair(null, null)
        }

        val cursor = db.rawQuery(
            if (selection != null) {
                "SELECT COUNT(*) FROM $TABLE_NAME WHERE $selection"
            } else {
                "SELECT COUNT(*) FROM $TABLE_NAME"
            },
            selectionArgs,
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun cursorToEntry(cursor: Cursor): LyricsCacheEntry {
        return LyricsCacheEntry(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            cacheKey = cursor.getString(cursor.getColumnIndexOrThrow(COL_CACHE_KEY)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
            artist = cursor.getString(cursor.getColumnIndexOrThrow(COL_ARTIST)),
            rawLrc = cursor.getString(cursor.getColumnIndexOrThrow(COL_RAW_LRC)),
            source = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE)),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DURATION_MS)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)),
        )
    }

    companion object {
        const val DATABASE_NAME = "lyrics_cache.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "lyrics_cache"
        const val COL_ID = "id"
        const val COL_CACHE_KEY = "cache_key"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_RAW_LRC = "raw_lrc"
        const val COL_SOURCE = "source"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_UPDATED_AT = "updated_at"

        @Volatile
        private var instance: LyricsDatabaseHelper? = null

        fun getInstance(context: Context): LyricsDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: LyricsDatabaseHelper(context.applicationContext ?: context).also { instance = it }
            }
        }
    }
}
