package com.lance.ytmusichyperlyric.plugin.lrclib

import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginContext
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult
import java.security.MessageDigest

/** Fetches a complete, timestamped lyric list and replaces only the lyrics field. */
internal class LrclibProcessor(private val context: PluginContext) : LyricProcessorExtension {
    override val id: String = "lrclib"
    override val stage = PluginProcessorStage.LYRIC_REPLACEMENT

    private val logger = context.logger.withTag("LrclibProcessor")
    private val client = LrclibClient(context.logger.withTag("LrclibClient"))

    override fun processResult(
        song: PluginSong,
        processingContext: PluginProcessingContext,
    ): PluginSongResult? {
        return try {
            processInternal(song, processingContext)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.debug("处理被中断")
            null
        } catch (error: Exception) {
            logger.warn("处理异常: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun processInternal(
        song: PluginSong,
        processingContext: PluginProcessingContext,
    ): PluginSongResult? {
        val config = LrclibConfig.from(context.config)
        if (!config.enabled) return null

        val media = processingContext.mediaInfo
        val title = media?.title?.takeIf { it.isNotBlank() } ?: song.name?.takeIf { it.isNotBlank() }
        val artist = media?.artist?.takeIf { it.isNotBlank() } ?: song.artist?.takeIf { it.isNotBlank() }
        if (title == null || artist == null) {
            logger.debug("跳过: 缺少标题或歌手")
            return null
        }
        val album = media?.album?.takeIf { it.isNotBlank() } ?: song.album?.takeIf { it.isNotBlank() }
        val durationMs = media?.duration?.takeIf { it > 0 } ?: song.duration.takeIf { it > 0 }
        val deadline = System.currentTimeMillis() + 30_000L

        for (queryTitle in LyricsNormalizer.titleCandidates(title)) {
            if (Thread.currentThread().isInterrupted) return null
            val cacheKey = cacheKey(queryTitle, artist, album, durationMs)
            val cached = context.cache.getString(cacheKey)
            val fetched = if (cached != null) {
                logger.debug("缓存命中: title=$queryTitle")
                FetchedLyrics(cached, source = "cache")
            } else {
                val fetchedFromNetwork = client.fetch(
                    config.endpoint,
                    queryTitle,
                    artist,
                    album,
                    durationMs,
                    deadline,
                ) ?: continue
                if (Thread.currentThread().isInterrupted) return null
                context.cache.putString(cacheKey, fetchedFromNetwork.syncedLyrics)
                fetchedFromNetwork
            }

            val lines = LrcParser.parse(fetched.syncedLyrics, durationMs ?: 0L)
            if (lines.isNullOrEmpty()) {
                context.cache.remove(cacheKey)
                logger.debug("歌词无有效时间轴: title=$queryTitle")
                continue
            }

            logger.info("歌词命中: lines=${lines.size}, source=${fetched.source}, title=$queryTitle")
            return PluginSongResult(
                song = song.copy(lyrics = lines),
                changedFields = setOf(PluginSongField.LYRICS),
                lyricsUpdateMode = PluginLyricsUpdateMode.REPLACE,
            )
        }
        logger.debug("LRCLIB 未命中，保持原歌词")
        return null
    }

    private fun cacheKey(title: String, artist: String, album: String?, durationMs: Long?): String {
        val source = listOf(title, artist, album.orEmpty(), durationMs?.toString().orEmpty())
            .joinToString("\u001f") { it.trim().lowercase() }
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return "lrc_" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
