package moe.lance.ytmusiclrc

import android.media.MediaMetadata

/** Private fields survive MediaSession IPC without changing AVRCP display fields. */
internal object OriginalSongMetadata {
    private const val MARKER = "moe.lance.ytmusiclrc.original"
    private val fields = listOf(
        MediaMetadata.METADATA_KEY_TITLE,
        MediaMetadata.METADATA_KEY_ARTIST,
        MediaMetadata.METADATA_KEY_ALBUM,
    )

    fun preserve(metadata: MediaMetadata): MediaMetadata = MediaMetadata.Builder(metadata).apply {
        putLong(MARKER, 1L)
        fields.forEach { putString("$MARKER.$it", metadata.getString(it).orEmpty()) }
    }.build()

    fun restore(metadata: MediaMetadata): MediaMetadata {
        if (metadata.getLong(MARKER) != 1L) return metadata
        return MediaMetadata.Builder(metadata).apply {
            fields.forEach { putString(it, metadata.getString("$MARKER.$it").orEmpty()) }
        }.build()
    }
}
