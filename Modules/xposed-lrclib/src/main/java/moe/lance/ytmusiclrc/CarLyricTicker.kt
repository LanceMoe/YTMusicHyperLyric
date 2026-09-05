package moe.lance.ytmusiclrc

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.proify.lyricon.lyric.model.RichLyricLine

/**
 * Event-driven lyric ticker that calculates current playback position and updates
 * MediaMetadata for Car Bluetooth (AVRCP) when the active lyric line changes.
 */
class CarLyricTicker(
    private val onUpdateMetadata: (MediaMetadata) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var originalMetadata: MediaMetadata? = null
    @Volatile private var lyrics: List<RichLyricLine>? = null
    @Volatile private var playbackState: PlaybackState? = null
    @Volatile private var config: CarBluetoothLyricConfig = CarBluetoothLyricConfig()
    @Volatile private var isBluetoothConnected: Boolean = false

    private var lastPublishedKey: String? = null
    private var isRunning = false

    private val tickRunnable = Runnable {
        tick()
    }

    fun setConfig(newConfig: CarBluetoothLyricConfig) {
        config = newConfig
        triggerImmediateUpdate()
    }

    fun setBluetoothConnected(connected: Boolean) {
        isBluetoothConnected = connected
        triggerImmediateUpdate()
    }

    fun setSong(metadata: MediaMetadata?, lines: List<RichLyricLine>?) {
        originalMetadata = metadata
        lyrics = lines
        lastPublishedKey = null
        triggerImmediateUpdate()
    }

    fun updateMetadata(metadata: MediaMetadata) {
        originalMetadata = metadata
        lastPublishedKey = null
        triggerImmediateUpdate()
    }

    fun updatePlaybackState(state: PlaybackState?) {
        playbackState = state
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) {
            start()
        } else {
            stop()
            // When paused/stopped, restore original metadata
            restoreOriginalMetadata()
        }
    }

    @Synchronized
    fun start() {
        if (!isRunning) {
            isRunning = true
            tick()
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    private fun triggerImmediateUpdate() {
        handler.removeCallbacks(tickRunnable)
        if (isRunning || playbackState?.state == PlaybackState.STATE_PLAYING) {
            isRunning = true
            tick()
        } else {
            restoreOriginalMetadata()
        }
    }

    private fun restoreOriginalMetadata() {
        val orig = originalMetadata ?: return
        val origTitle = orig.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val origArtist = orig.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val origAlbum = orig.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val key = "$origTitle\u0000$origArtist\u0000$origAlbum"
        if (lastPublishedKey != key) {
            lastPublishedKey = key
            onUpdateMetadata(orig)
            Log.d(LrclibXposedModule.TAG, "Restored original metadata: $origTitle — $origArtist")
        }
    }

    private fun tick() {
        handler.removeCallbacks(tickRunnable)
        val orig = originalMetadata
        val state = playbackState

        if (orig == null || state == null || state.state != PlaybackState.STATE_PLAYING) {
            isRunning = false
            restoreOriginalMetadata()
            return
        }

        val shouldDisplayLyric = config.enabled &&
            (!config.onlyWhenBluetooth || isBluetoothConnected) &&
            !lyrics.isNullOrEmpty()

        if (!shouldDisplayLyric) {
            restoreOriginalMetadata()
            // Check again after 1 second in case bluetooth connects or config changes
            handler.postDelayed(tickRunnable, 1000L)
            return
        }

        val speed = state.playbackSpeed.takeIf { it > 0f } ?: 1.0f
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        val currentPosMs = compensatedPositionMs(state.position, elapsed, speed, config.offsetMs)

        val lines = lyrics.orEmpty()
        val activeLine = lines.firstOrNull { it.begin <= currentPosMs && currentPosMs < it.end }

        val origTitle = orig.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val origArtist = orig.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val origAlbum = orig.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

        val (targetTitle, targetArtist, targetAlbum) = formatMetadata(
            origTitle = origTitle,
            origArtist = origArtist,
            origAlbum = origAlbum,
            activeLine = activeLine,
            mode = config.displayMode,
        )

        val newKey = "$targetTitle\u0000$targetArtist\u0000$targetAlbum"
        if (newKey != lastPublishedKey) {
            lastPublishedKey = newKey
            val newMetadata = MediaMetadata.Builder(OriginalSongMetadata.preserve(orig))
                .putString(MediaMetadata.METADATA_KEY_TITLE, targetTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, targetArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, targetAlbum)
                .build()
            onUpdateMetadata(newMetadata)
            Log.d(
                LrclibXposedModule.TAG,
                "Published car lyric update: title='$targetTitle' artist='$targetArtist' mode=${config.displayMode}",
            )
        }

        // Schedule next wake-up exactly when current line ends or next line begins
        val nextTransitionTimeMs = if (activeLine != null) {
            activeLine.end
        } else {
            lines.firstOrNull { it.begin > currentPosMs }?.begin
        }

        val delayMs = if (nextTransitionTimeMs != null && nextTransitionTimeMs > currentPosMs) {
            ((nextTransitionTimeMs - currentPosMs) / speed).toLong().coerceIn(100L, 2000L)
        } else {
            1000L
        }

        handler.postDelayed(tickRunnable, delayMs)
    }

    companion object {
        internal fun compensatedPositionMs(positionMs: Long, elapsedMs: Long, speed: Float, offsetMs: Long): Long {
            // Positive compensation delays lyrics; keep negative positions so a line
            // starting at zero is also delayed and the next wake-up uses the full offset.
            return positionMs + (elapsedMs * speed).toLong() - offsetMs
        }

        fun formatMetadata(
            origTitle: String,
            origArtist: String,
            origAlbum: String,
            activeLine: RichLyricLine?,
            mode: LyricDisplayMode,
        ): Triple<String, String, String> {
            val text = activeLine?.text?.trim()
            if (text.isNullOrBlank()) {
                return Triple(origTitle, origArtist, origAlbum)
            }

            return when (mode) {
                LyricDisplayMode.TITLE_ONLY -> Triple(text, origArtist, origAlbum)
                LyricDisplayMode.TITLE_WITH_SONG -> {
                    val combined = if (origTitle.isNotBlank()) "$origTitle - $text" else text
                    Triple(combined, origArtist, origAlbum)
                }
                LyricDisplayMode.ARTIST_ONLY -> Triple(origTitle, text, origAlbum)
                LyricDisplayMode.ALBUM_ONLY -> Triple(origTitle, origArtist, text)
            }
        }
    }
}
