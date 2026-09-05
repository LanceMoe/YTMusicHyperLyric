package moe.lance.ytmusiclrc

import android.app.Application
import android.content.ContextWrapper
import android.media.MediaMetadata
import android.os.Looper
import android.os.Parcel
import android.os.Process
import moe.lance.ytmusiclrc.cache.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class AndroidRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun entry(duration: Long, title: String = "晴天😀 (Official Video)") = LyricsCacheEntry(
        cacheKey = LyricsRepository.buildCacheKey(title, "周杰伦 - Topic", duration),
        title = title, artist = "周杰伦 - Topic", durationMs = duration,
        rawLrc = "[00:00.00]original\n[00:10.00]second", source = "test",
    )

    @Test fun originalSongSurvivesParcelInEveryCarDisplayMode() {
        val original = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Song")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Album")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 180_000).build()
        for (field in listOf(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM)) {
            val synthetic = MediaMetadata.Builder(OriginalSongMetadata.preserve(original)).putString(field, "Lyric line").build()
            val parcel = Parcel.obtain()
            try {
                synthetic.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                val restored = OriginalSongMetadata.restore(MediaMetadata.CREATOR.createFromParcel(parcel))
                assertEquals("Song", restored.getString(MediaMetadata.METADATA_KEY_TITLE))
                assertEquals("Artist", restored.getString(MediaMetadata.METADATA_KEY_ARTIST))
                assertEquals("Album", restored.getString(MediaMetadata.METADATA_KEY_ALBUM))
                assertEquals(180_000L, restored.getLong(MediaMetadata.METADATA_KEY_DURATION))
            } finally { parcel.recycle() }
        }
    }

    @Test fun embeddedNulAndUnicodeKeysMatchAcrossDurationBucketsAndDeduplicate() {
        LyricsDatabaseHelper(context).use { db ->
            db.deleteAll()
            val zero = entry(0)
            assertTrue(db.insertOrUpdate(zero))
            val full = entry(240_000)
            assertEquals(zero.cacheKey, db.findBestMatch(full.cacheKey, "晴天😀", "周杰伦", 240_000)?.cacheKey)
            assertTrue(db.insertOrUpdate(full))
            assertNull(db.get(zero.cacheKey))
            assertEquals(full.cacheKey, db.findBestMatch(entry(246_000).cacheKey, "晴天😀", "周杰伦", 246_000)?.cacheKey)
            assertTrue(db.insertOrUpdate(zero))
            assertEquals(1, db.getCount())
        }
        // Reopening used to recursively call getWritableDatabase from onOpen.
        LyricsDatabaseHelper(context).use { assertEquals(1, it.getCount()) }
    }

    @Test fun manualEditsAndDeletesOverrideAlreadyLoadedMemoryAndNotifyObservers() {
        val hostContext = object : ContextWrapper(context) {
            override fun getPackageName() = "moe.lance.ytmusiclrc"
        }
        val db = LyricsDatabaseHelper.getInstance(hostContext)
        db.deleteAll()
        val song = entry(240_000)
        db.insertOrUpdate(song)
        fun load() = LyricsRepository.getLyrics(song.title, song.artist, "", song.durationMs, hostContext, false)
        assertEquals("original", load()?.first()?.text)
        var changes = 0
        LyricsCacheChanges.observe(hostContext) { changes++ }
        db.updateLrc(song.cacheKey, "[00:00.00]edited")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(changes > 0)
        assertEquals("edited", load()?.first()?.text)
        db.delete(song.cacheKey)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(changes >= 2)
        assertNull(load())
    }

    @Test fun providerAllowsBothInjectedHostUids() {
        val provider = Robolectric.buildContentProvider(LyricsCacheProvider::class.java).create().get()
        val uid = Process.myUid() + 12345
        try {
            for (host in listOf("com.android.systemui", "com.google.android.apps.youtube.music")) {
                shadowOf(context.packageManager).setPackagesForUid(uid, host)
                ShadowBinder.setCallingUid(uid)
                assertNotNull(provider.call("getCount", null, null))
            }
        } finally { ShadowBinder.setCallingUid(Process.myUid()) }
    }

    @Test fun providerRejectsUntrustedCallersForEveryDataEntryPoint() {
        val provider = Robolectric.buildContentProvider(LyricsCacheProvider::class.java).create().get()
        ShadowBinder.setCallingUid(Process.myUid() + 12345)
        val uri = LyricsCacheProvider.CONTENT_URI
        val actions = listOf<() -> Any?>(
            { provider.call("deleteAll", null, null) },
            { provider.query(uri, null, null, null, null) },
            { provider.insert(uri, null) },
            { provider.update(uri, null, null, null) },
            { provider.delete(uri, null, null) },
            { provider.getType(uri) },
        )
        try {
            actions.forEach { action ->
                try { action(); fail("Untrusted caller was accepted") } catch (_: SecurityException) { }
            }
        } finally { ShadowBinder.setCallingUid(Process.myUid()) }
        assertNotNull(provider.call("getCount", null, null))
    }
}
