package moe.lance.ytmusiclrc.cache

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
    private val appContext = context.applicationContext

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

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        deduplicateExisting(db)
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

        val db = readableDatabase
        val candidates = mutableListOf<LyricsCacheEntry>()

        // 2. Match by normalized song prefix from cacheKey (normTitle\u0000normArtist\u0000)
        val prefix = if (cacheKey.contains('\u0000')) {
            cacheKey.substringBeforeLast('\u0000') + "\u0000"
        } else {
            ""
        }

        // SQLite text substr stops at NUL; BLOB comparison also needs UTF-8 byte length.
        if (prefix.isNotEmpty()) {
            val prefixCursor = db.query(
                TABLE_NAME,
                null,
                "substr(CAST($COL_CACHE_KEY AS BLOB), 1, ?) = CAST(? AS BLOB) AND TRIM($COL_RAW_LRC) != ''",
                arrayOf(prefix.toByteArray(Charsets.UTF_8).size.toString(), prefix),
                null,
                null,
                "$COL_UPDATED_AT DESC",
                "10",
            )
            prefixCursor.use {
                while (it.moveToNext()) {
                    val entry = cursorToEntry(it)
                    if (entry.hasLyrics) candidates.add(entry)
                }
            }
        }

        // 3. Fallback: match by title and artist
        if (candidates.isEmpty() && cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
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
            cursor.use {
                while (it.moveToNext()) {
                    val entry = cursorToEntry(it)
                    if (entry.hasLyrics) candidates.add(entry)
                }
            }
        }

        if (candidates.isEmpty()) return exact

        // 4. Select best candidate
        if (durationMs > 0L) {
            // If durationMs is known, prefer a candidate with duration within 15s tolerance
            var closest: LyricsCacheEntry? = null
            var minDiff = Long.MAX_VALUE
            for (candidate in candidates) {
                if (candidate.durationMs > 0L) {
                    val diff = abs(candidate.durationMs - durationMs)
                    if (diff <= 15_000L && diff < minDiff) {
                        minDiff = diff
                        closest = candidate
                    }
                }
            }
            if (closest != null) return closest

            // If no candidate has matching duration, fallback to candidate with durationMs <= 0
            val zeroDurationCandidate = candidates.firstOrNull { it.durationMs <= 0L }
            if (zeroDurationCandidate != null) return zeroDurationCandidate
        } else {
            // durationMs <= 0L: prefer candidate with full duration if available, else candidate with 0 duration
            val fullDurationCandidate = candidates.firstOrNull { it.durationMs > 0L }
            if (fullDurationCandidate != null) return fullDurationCandidate
            return candidates.firstOrNull()
        }

        return exact
    }

    @Synchronized
    fun insertOrUpdate(entry: LyricsCacheEntry): Boolean {
        val db = writableDatabase
        val prefix = if (entry.cacheKey.contains('\u0000')) {
            entry.cacheKey.substringBeforeLast('\u0000') + "\u0000"
        } else {
            ""
        }

        // If saving an entry with full duration (> 0) and valid lyrics,
        // remove any obsolete 0-duration entry for the same song to prevent duplicates.
        if (entry.hasLyrics && entry.durationMs > 0L && prefix.isNotEmpty()) {
            db.delete(
                TABLE_NAME,
                "substr(CAST($COL_CACHE_KEY AS BLOB), 1, ?) = CAST(? AS BLOB) AND $COL_DURATION_MS <= 0",
                arrayOf(prefix.toByteArray(Charsets.UTF_8).size.toString(), prefix),
            )
        } else if (entry.durationMs <= 0L && prefix.isNotEmpty()) {
            // If saving an entry with durationMs <= 0, check if an entry with full duration already exists.
            val hasBetterEntry = db.query(
                TABLE_NAME,
                arrayOf(COL_ID),
                "substr(CAST($COL_CACHE_KEY AS BLOB), 1, ?) = CAST(? AS BLOB) AND $COL_DURATION_MS > 0 AND TRIM($COL_RAW_LRC) != ''",
                arrayOf(prefix.toByteArray(Charsets.UTF_8).size.toString(), prefix),
                null,
                null,
                null,
                "1",
            ).use { it.moveToFirst() }

            if (hasBetterEntry) {
                // Do not create duplicate 0-duration entry when a complete entry already exists.
                return true
            }
        }

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
    fun deduplicateExisting(): Int = deduplicateExisting(writableDatabase)

    private fun deduplicateExisting(db: SQLiteDatabase): Int {
        val zeroEntriesCursor = db.query(
            TABLE_NAME,
            arrayOf(COL_ID, COL_CACHE_KEY),
            "$COL_DURATION_MS <= 0",
            null,
            null,
            null,
            null,
        )

        val idsToDelete = mutableListOf<Long>()
        zeroEntriesCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val key = cursor.getString(1)
                val prefix = if (key.contains('\u0000')) {
                    key.substringBeforeLast('\u0000') + "\u0000"
                } else {
                    ""
                }

                val hasBetter = if (prefix.isNotEmpty()) {
                    db.query(
                        TABLE_NAME,
                        arrayOf(COL_ID),
                        "substr(CAST($COL_CACHE_KEY AS BLOB), 1, ?) = CAST(? AS BLOB) AND $COL_DURATION_MS > 0 AND TRIM($COL_RAW_LRC) != ''",
                        arrayOf(prefix.toByteArray(Charsets.UTF_8).size.toString(), prefix),
                        null,
                        null,
                        null,
                        "1",
                    ).use { it.moveToFirst() }
                } else {
                    false
                }

                if (hasBetter) {
                    idsToDelete.add(id)
                }
            }
        }

        var deletedCount = 0
        if (idsToDelete.isNotEmpty()) {
            db.beginTransaction()
            try {
                for (id in idsToDelete) {
                    deletedCount += db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return deletedCount
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
        if (rows > 0) LyricsCacheChanges.notify(appContext)
        return rows > 0
    }

    @Synchronized
    fun delete(cacheKey: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_NAME, "$COL_CACHE_KEY = ?", arrayOf(cacheKey))
        if (rows > 0) LyricsCacheChanges.notify(appContext)
        return rows > 0
    }

    @Synchronized
    fun deleteById(id: Long): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
        if (rows > 0) LyricsCacheChanges.notify(appContext)
        return rows > 0
    }

    @Synchronized
    fun deleteAll(): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, null, null).also { LyricsCacheChanges.notify(appContext) }
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
