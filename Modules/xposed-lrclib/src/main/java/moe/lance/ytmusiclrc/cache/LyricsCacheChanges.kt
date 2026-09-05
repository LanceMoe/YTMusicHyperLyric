package moe.lance.ytmusiclrc.cache

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import moe.lance.ytmusiclrc.LyricsRepository
import moe.lance.ytmusiclrc.LrclibXposedModule

internal object LyricsCacheChanges {
    fun notify(context: Context) {
        LyricsRepository.clearMemoryCache()
        context.contentResolver.notifyChange(LyricsCacheProvider.CONTENT_URI, null)
    }

    /** Process-lifetime observers are held by the ContentResolver. */
    fun observe(context: Context, onChange: () -> Unit) {
        runCatching {
            context.contentResolver.registerContentObserver(
                LyricsCacheProvider.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        LyricsRepository.clearMemoryCache()
                        onChange()
                    }
                },
            )
        }.onFailure { Log.w(LrclibXposedModule.TAG, "Cannot observe lyric cache edits", it) }
    }
}
