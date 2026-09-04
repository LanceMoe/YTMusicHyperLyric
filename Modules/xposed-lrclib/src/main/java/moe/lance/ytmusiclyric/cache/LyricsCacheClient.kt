package moe.lance.ytmusiclyric.cache

import android.content.Context
import android.os.Bundle
import android.util.Log
import moe.lance.ytmusiclyric.LrclibXposedModule

object LyricsCacheClient {

    fun get(
        context: Context,
        cacheKey: String,
        cleanTitle: String = "",
        cleanArtist: String = "",
        durationMs: Long = 0L,
    ): LyricsCacheEntry? {
        return runCatching {
            if (isHostAppProcess(context)) {
                val db = LyricsDatabaseHelper.getInstance(context)
                if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                    db.findBestMatch(cacheKey, cleanTitle, cleanArtist, durationMs)
                } else {
                    db.get(cacheKey)
                }
            } else {
                val extras = Bundle().apply {
                    putString("cleanTitle", cleanTitle)
                    putString("cleanArtist", cleanArtist)
                    putLong("durationMs", durationMs)
                }
                val response = context.contentResolver.call(
                    LyricsCacheProvider.CONTENT_URI,
                    LyricsCacheProvider.METHOD_GET_LYRIC,
                    cacheKey,
                    extras,
                ) ?: return null

                if (response.getBoolean(LyricsCacheProvider.EXTRA_FOUND, false)) {
                    LyricsCacheEntry(
                        cacheKey = response.getString(LyricsCacheProvider.EXTRA_CACHE_KEY, cacheKey),
                        title = response.getString(LyricsCacheProvider.EXTRA_TITLE).orEmpty(),
                        artist = response.getString(LyricsCacheProvider.EXTRA_ARTIST).orEmpty(),
                        rawLrc = response.getString(LyricsCacheProvider.EXTRA_RAW_LRC).orEmpty(),
                        source = response.getString(LyricsCacheProvider.EXTRA_SOURCE).orEmpty(),
                        durationMs = response.getLong(LyricsCacheProvider.EXTRA_DURATION_MS, 0L),
                        updatedAt = response.getLong(LyricsCacheProvider.EXTRA_UPDATED_AT, 0L),
                    )
                } else {
                    null
                }
            }
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LyricsCacheClient.get failed for key='$cacheKey'", error)
        }.getOrNull()
    }

    fun save(context: Context, entry: LyricsCacheEntry): Boolean {
        return runCatching {
            if (isHostAppProcess(context)) {
                LyricsDatabaseHelper.getInstance(context).insertOrUpdate(entry)
            } else {
                val extras = Bundle().apply {
                    putString(LyricsCacheProvider.EXTRA_TITLE, entry.title)
                    putString(LyricsCacheProvider.EXTRA_ARTIST, entry.artist)
                    putString(LyricsCacheProvider.EXTRA_RAW_LRC, entry.rawLrc)
                    putString(LyricsCacheProvider.EXTRA_SOURCE, entry.source)
                    putLong(LyricsCacheProvider.EXTRA_DURATION_MS, entry.durationMs)
                    putLong(LyricsCacheProvider.EXTRA_UPDATED_AT, entry.updatedAt)
                }
                val response = context.contentResolver.call(
                    LyricsCacheProvider.CONTENT_URI,
                    LyricsCacheProvider.METHOD_PUT_LYRIC,
                    entry.cacheKey,
                    extras,
                )
                response?.getBoolean(LyricsCacheProvider.EXTRA_SUCCESS, false) ?: false
            }
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LyricsCacheClient.save failed for '${entry.title}'", error)
        }.getOrDefault(false)
    }

    fun updateLrc(context: Context, cacheKey: String, rawLrc: String, source: String = "自定义编辑"): Boolean {
        return runCatching {
            if (isHostAppProcess(context)) {
                LyricsDatabaseHelper.getInstance(context).updateLrc(cacheKey, rawLrc, source)
            } else {
                val extras = Bundle().apply {
                    putString(LyricsCacheProvider.EXTRA_RAW_LRC, rawLrc)
                    putString(LyricsCacheProvider.EXTRA_SOURCE, source)
                }
                val response = context.contentResolver.call(
                    LyricsCacheProvider.CONTENT_URI,
                    LyricsCacheProvider.METHOD_UPDATE_LRC,
                    cacheKey,
                    extras,
                )
                response?.getBoolean(LyricsCacheProvider.EXTRA_SUCCESS, false) ?: false
            }
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LyricsCacheClient.updateLrc failed for key='$cacheKey'", error)
        }.getOrDefault(false)
    }

    fun delete(context: Context, cacheKey: String): Boolean {
        return runCatching {
            if (isHostAppProcess(context)) {
                LyricsDatabaseHelper.getInstance(context).delete(cacheKey)
            } else {
                val response = context.contentResolver.call(
                    LyricsCacheProvider.CONTENT_URI,
                    LyricsCacheProvider.METHOD_DELETE_LYRIC,
                    cacheKey,
                    null,
                )
                response?.getBoolean(LyricsCacheProvider.EXTRA_SUCCESS, false) ?: false
            }
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LyricsCacheClient.delete failed for key='$cacheKey'", error)
        }.getOrDefault(false)
    }

    fun deleteAll(context: Context): Int {
        return runCatching {
            if (isHostAppProcess(context)) {
                LyricsDatabaseHelper.getInstance(context).deleteAll()
            } else {
                val response = context.contentResolver.call(
                    LyricsCacheProvider.CONTENT_URI,
                    LyricsCacheProvider.METHOD_DELETE_ALL,
                    null,
                    null,
                )
                response?.getInt(LyricsCacheProvider.EXTRA_COUNT, 0) ?: 0
            }
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LyricsCacheClient.deleteAll failed", error)
        }.getOrDefault(0)
    }

    private fun isHostAppProcess(context: Context): Boolean {
        return context.packageName == LrclibXposedModule.MODULE_PACKAGE
    }
}

