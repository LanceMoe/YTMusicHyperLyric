package moe.lance.ytmusiclrc.cache

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class LyricsCacheProvider : ContentProvider() {

    private lateinit var dbHelper: LyricsDatabaseHelper

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        dbHelper = LyricsDatabaseHelper.getInstance(ctx)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_GET_LYRIC -> {
                val cacheKey = arg ?: return null
                val cleanTitle = extras?.getString("cleanTitle").orEmpty()
                val cleanArtist = extras?.getString("cleanArtist").orEmpty()
                val durationMs = extras?.getLong("durationMs", 0L) ?: 0L

                val entry = if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                    dbHelper.findBestMatch(cacheKey, cleanTitle, cleanArtist, durationMs)
                } else {
                    dbHelper.get(cacheKey)
                }

                Bundle().apply {
                    if (entry != null) {
                        putBoolean(EXTRA_FOUND, true)
                        putString(EXTRA_CACHE_KEY, entry.cacheKey)
                        putString(EXTRA_TITLE, entry.title)
                        putString(EXTRA_ARTIST, entry.artist)
                        putString(EXTRA_RAW_LRC, entry.rawLrc)
                        putString(EXTRA_SOURCE, entry.source)
                        putLong(EXTRA_DURATION_MS, entry.durationMs)
                        putLong(EXTRA_UPDATED_AT, entry.updatedAt)
                    } else {
                        putBoolean(EXTRA_FOUND, false)
                    }
                }
            }

            METHOD_PUT_LYRIC -> {
                val cacheKey = arg ?: return null
                val ex = extras ?: return null
                val entry = LyricsCacheEntry(
                    cacheKey = cacheKey,
                    title = ex.getString(EXTRA_TITLE).orEmpty(),
                    artist = ex.getString(EXTRA_ARTIST).orEmpty(),
                    rawLrc = ex.getString(EXTRA_RAW_LRC).orEmpty(),
                    source = ex.getString(EXTRA_SOURCE).orEmpty(),
                    durationMs = ex.getLong(EXTRA_DURATION_MS, 0L),
                    updatedAt = ex.getLong(EXTRA_UPDATED_AT, System.currentTimeMillis()),
                )
                val success = dbHelper.insertOrUpdate(entry)
                Bundle().apply { putBoolean(EXTRA_SUCCESS, success) }
            }

            METHOD_UPDATE_LRC -> {
                val cacheKey = arg ?: return null
                val rawLrc = extras?.getString(EXTRA_RAW_LRC).orEmpty()
                val source = extras?.getString(EXTRA_SOURCE) ?: "自定义编辑"
                val success = dbHelper.updateLrc(cacheKey, rawLrc, source)
                Bundle().apply { putBoolean(EXTRA_SUCCESS, success) }
            }

            METHOD_DELETE_LYRIC -> {
                val cacheKey = arg ?: return null
                val success = dbHelper.delete(cacheKey)
                Bundle().apply { putBoolean(EXTRA_SUCCESS, success) }
            }

            METHOD_DELETE_ALL -> {
                val count = dbHelper.deleteAll()
                Bundle().apply { putInt(EXTRA_COUNT, count) }
            }

            METHOD_GET_COUNT -> {
                val kw = extras?.getString("keyword")
                val count = dbHelper.getCount(kw)
                Bundle().apply { putInt(EXTRA_COUNT, count) }
            }

            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        return dbHelper.readableDatabase.query(
            LyricsDatabaseHelper.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder,
        )
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/moe.lance.ytmusiclrc.cache"

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val v = values ?: return null
        val id = dbHelper.writableDatabase.insert(LyricsDatabaseHelper.TABLE_NAME, null, v)
        return if (id != -1L) Uri.withAppendedPath(uri, id.toString()) else null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return dbHelper.writableDatabase.delete(LyricsDatabaseHelper.TABLE_NAME, selection, selectionArgs)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return dbHelper.writableDatabase.update(LyricsDatabaseHelper.TABLE_NAME, values, selection, selectionArgs)
    }

    companion object {
        const val AUTHORITY = "moe.lance.ytmusiclrc.cache"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

        const val METHOD_GET_LYRIC = "getLyric"
        const val METHOD_PUT_LYRIC = "putLyric"
        const val METHOD_UPDATE_LRC = "updateLrc"
        const val METHOD_DELETE_LYRIC = "deleteLyric"
        const val METHOD_DELETE_ALL = "deleteAll"
        const val METHOD_GET_COUNT = "getCount"

        const val EXTRA_FOUND = "found"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_COUNT = "count"
        const val EXTRA_CACHE_KEY = "cache_key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_RAW_LRC = "raw_lrc"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_UPDATED_AT = "updated_at"
    }
}
