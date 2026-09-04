package com.lance.ytmusichyperlyric.xposed

import android.app.Application
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * A conventional LSPosed module. It repairs HyperLyric's ZIP loading boundary on Android/HyperOS
 * and also supports a direct Lyricon provider when Lyricon Central is installed.
 */
class LrclibXposedModule : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Log.i(TAG, "LSPosed module loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != SYSTEM_UI || Application.getProcessName().contains(':')) return

        installPluginDexReadOnlyHook(param)
        runCatching {
            val appClass = param.defaultClassLoader.loadClass("android.app.Application")
            val onCreate = appClass.getDeclaredMethod("onCreate")
            deoptimize(onCreate)
            hook(onCreate).intercept(ApplicationCreateHook())
            Log.i(TAG, "Application.onCreate hook installed")
        }.onFailure { error ->
            Log.e(TAG, "Could not install SystemUI lifecycle hook", error)
        }
    }

    /** Make HyperLyric's materialized plugin archive read-only before ART loads its DEX. */
    private fun installPluginDexReadOnlyHook(param: PackageLoadedParam) {
        runCatching {
            val baseDexClassLoader = Class.forName(
                "dalvik.system.BaseDexClassLoader",
                false,
                param.defaultClassLoader,
            )
            baseDexClassLoader.declaredConstructors.forEach { constructor ->
                deoptimize(constructor)
                hook(constructor).intercept(object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        chain.args.filterIsInstance<String>()
                            .firstOrNull { it.contains("hyperlyric_plugin_dex") }
                            ?.let { path ->
                                val archive = File(path)
                                if (archive.exists() && archive.setReadOnly()) {
                                    Log.i(TAG, "Marked HyperLyric plugin archive read-only before ART load")
                                }
                            }
                        return chain.proceed()
                    }
                })
            }
            Log.i(TAG, "HyperLyric plugin DEX compatibility hook installed")
        }.onFailure { error ->
            Log.e(TAG, "Could not install plugin DEX compatibility hook", error)
        }
    }

    private inner class ApplicationCreateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            (chain.thisObject as? Application)?.let(::initializeOnce)
            return result
        }
    }

    private fun initializeOnce(app: Application) {
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            val provider = LyriconFactory.createProvider(
                context = app,
                providerPackageName = MODULE_PACKAGE,
                playerPackageName = YT_MUSIC,
                processName = Application.getProcessName(),
                centralPackageName = SYSTEM_UI,
            )
            val registrationHandler = Handler(Looper.getMainLooper())
            val registrationAttempts = AtomicInteger(0)
            fun retryRegistration(reason: String) {
                if (provider.service.isActive) return
                val attempt = registrationAttempts.incrementAndGet()
                if (attempt > MAX_REGISTRATION_ATTEMPTS) {
                    Log.e(TAG, "Lyricon registration abandoned after $MAX_REGISTRATION_ATTEMPTS attempts")
                    return
                }
                val started = provider.register()
                Log.i(
                    TAG,
                    "Lyricon registration retry#$attempt reason=$reason started=$started " +
                        "active=${provider.service.isActive}",
                )
            }
            provider.service.addConnectionListener(object : ConnectionListener {
                override fun onConnected(provider: LyriconProvider) {
                    Log.i(TAG, "Lyricon connection established")
                }

                override fun onReconnected(provider: LyriconProvider) {
                    Log.i(TAG, "Lyricon connection re-established")
                }

                override fun onDisconnected(provider: LyriconProvider) {
                    Log.w(TAG, "Lyricon connection lost")
                }

                override fun onConnectTimeout(provider: LyriconProvider) {
                    Log.e(TAG, "Lyricon connection timed out")
                    registrationHandler.postDelayed({ retryRegistration("timeout") }, 250L)
                }
            })
            val bridge = YtMusicLyricsBridge(app, provider)
            provider.service.addConnectionListener(object : ConnectionListener {
                override fun onConnected(provider: LyriconProvider) = bridge.start()
                override fun onReconnected(provider: LyriconProvider) = bridge.start()
                override fun onDisconnected(provider: LyriconProvider) = Unit
                override fun onConnectTimeout(provider: LyriconProvider) = Unit
            })
            val registrationStarted = provider.register()
            Log.i(
                TAG,
                "Lyricon provider registration started=$registrationStarted, " +
                    "active=${provider.service.isActive}; waiting for connection"
            )
            // Lyricon Core and this module are both initialized from Application.onCreate.
            // Retry after Core has registered its bridge receiver in case our first broadcast
            // wins the startup race.
            registrationHandler.postDelayed({ retryRegistration("startup") }, 750L)
        }.onFailure { error ->
            initialized.set(false)
            Log.e(TAG, "SystemUI initialization failed", error)
        }
    }

    companion object {
        const val TAG = "YTMusicHyperLyric"
        const val SYSTEM_UI = "com.android.systemui"
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
        const val MODULE_PACKAGE = "com.lance.ytmusichyperlyric.xposed"
        const val MAX_REGISTRATION_ATTEMPTS = 5
        val initialized = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}

private class YtMusicLyricsBridge(
    private val app: Application,
    private val provider: LyriconProvider,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ytmusic-lrclib").apply { isDaemon = true }
    }
    private val sequence = AtomicLong(0)
    private val lyricsCache = ConcurrentHashMap<String, List<RichLyricLine>>()

    @Volatile private var activeToken: MediaSession.Token? = null
    @Volatile private var activeController: MediaController? = null
    @Volatile private var activeCallback: MediaController.Callback? = null
    @Volatile private var currentKey: String? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectController(controllers.orEmpty())
    }

    fun start() {
        val manager = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: error("MediaSessionManager is unavailable")
        manager.addOnActiveSessionsChangedListener(sessionListener, null, mainHandler)
        selectController(manager.getActiveSessions(null).orEmpty())
    }

    private fun selectController(controllers: List<MediaController>) {
        val controller = controllers.firstOrNull { it.packageName == LrclibXposedModule.YT_MUSIC }
        if (controller?.sessionToken == activeToken) return

        activeController?.let { old -> activeCallback?.let(old::unregisterCallback) }
        activeToken = controller?.sessionToken
        activeController = controller
        activeCallback = null
        currentKey = null
        sequence.incrementAndGet()
        provider.player.setSong(null)

        if (controller == null) {
            Log.d(LrclibXposedModule.TAG, "YT Music MediaSession not active")
            return
        }

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.let { onMetadata(controller, it) }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state?.let { publishPlayback(it) }
            }

            override fun onSessionDestroyed() {
                if (controller.sessionToken == activeToken) selectController(emptyList())
            }
        }
        activeCallback = callback
        controller.registerCallback(callback, mainHandler)
        controller.metadata?.let { onMetadata(controller, it) }
        controller.playbackState?.let(::publishPlayback)
        Log.i(LrclibXposedModule.TAG, "Using YT Music MediaSession")
    }

    private fun onMetadata(controller: MediaController, metadata: MediaMetadata) {
        if (controller.sessionToken != activeToken) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        if (title.isBlank() || artist.isBlank()) {
            Log.d(LrclibXposedModule.TAG, "Skip incomplete metadata: title=$title, artist=$artist")
            return
        }

        val key = "$title\\u0000$artist\\u0000$album\\u0000$duration"
        if (key == currentKey) return
        currentKey = key
        val request = sequence.incrementAndGet()
        provider.player.setSong(null)
        lyricsCache[key]?.let { lines ->
            publishIfCurrent(request, key, title, artist, duration, lines, controller)
            return
        }

        executor.execute {
            val lines = runCatching {
                LrclibDirectClient.fetch(title, artist, album, duration)
                    ?.let { LrcToLyricon.parse(it, duration) }
            }.onFailure { error ->
                Log.w(LrclibXposedModule.TAG, "LRCLIB lookup failed", error)
            }.getOrNull()
            if (lines.isNullOrEmpty()) {
                Log.i(LrclibXposedModule.TAG, "No timed LRCLIB lyrics: $title — $artist")
                return@execute
            }
            lyricsCache[key] = lines
            while (lyricsCache.size > 32) {
                lyricsCache.keys.firstOrNull()?.let(lyricsCache::remove) ?: break
            }
            mainHandler.post {
                publishIfCurrent(request, key, title, artist, duration, lines, controller)
            }
        }
    }

    private fun publishIfCurrent(
        request: Long,
        key: String,
        title: String,
        artist: String,
        duration: Long,
        lines: List<RichLyricLine>,
        controller: MediaController,
    ) {
        if (request != sequence.get() || key != currentKey || controller.sessionToken != activeToken) return
        val accepted = provider.player.setSong(
            Song(id = key, name = title, artist = artist, duration = duration, lyrics = lines)
        )
        controller.playbackState?.let(::publishPlayback)
        Log.i(
            LrclibXposedModule.TAG,
            "Published ${lines.size} LRCLIB lines: $title — $artist; " +
                "accepted=$accepted active=${provider.service.isActive}"
        )
    }

    private fun publishPlayback(state: PlaybackState) {
        // Keep Lyricon's automatic playback-state synchronization enabled. Passing only the
        // boolean state and a point-in-time position switches CachedRemotePlayer to manual mode,
        // which stops advancing the timeline when no new MediaSession callback arrives (for
        // example after YT Music goes to the background).
        provider.player.setPlaybackState(state)
    }
}

private object LrclibDirectClient {
    private const val GET = "https://lrclib.net/api/get"
    private const val SEARCH = "https://lrclib.net/api/search"
    private const val TIMEOUT_MS = 10_000

    fun fetch(title: String, artist: String, album: String, durationMs: Long): String? {
        request(GET, buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            album.takeIf(String::isNotBlank)?.let { add("album_name" to it) }
            durationMs.takeIf { it > 0 }?.let { add("duration" to (it / 1_000.0).toString()) }
        })?.let { JSONObject(it).optString("syncedLyrics").takeIf(::hasLyrics) }?.let { return it }

        val candidates = request(SEARCH, listOf("q" to "$title $artist"))
            ?.let(::JSONArray)
            ?.let { response ->
                buildList {
                    for (index in 0 until response.length()) {
                        val item = response.optJSONObject(index) ?: continue
                        val lyrics = item.optString("syncedLyrics").takeIf(::hasLyrics) ?: continue
                        add(
                            SearchCandidate(
                                item.optString("trackName"),
                                item.optString("artistName"),
                                item.optDouble("duration", Double.NaN).takeUnless(Double::isNaN)?.times(1_000)?.toLong(),
                                lyrics,
                            ),
                        )
                    }
                }
            }.orEmpty()
        return candidates.best(title, artist, durationMs)?.lyrics
    }

    private fun request(endpoint: String, params: List<Pair<String, String>>): String? {
        val query = params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}" }
        val connection = (URL("$endpoint?$query").openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "YTMusicHyperLyric/0.2")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().takeIf { body -> body.length <= 1_000_000 } }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun hasLyrics(value: String) = value.isNotBlank() && value != "null"

    private data class SearchCandidate(val title: String, val artist: String, val duration: Long?, val lyrics: String)

    private fun List<SearchCandidate>.best(title: String, artist: String, duration: Long): SearchCandidate? {
        fun normal(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
        val expectedTitle = normal(title)
        val expectedArtist = normal(artist)
        return mapNotNull { candidate ->
            val candidateTitle = normal(candidate.title)
            val candidateArtist = normal(candidate.artist)
            val titleScore = when {
                candidateTitle == expectedTitle -> 100
                candidateTitle.contains(expectedTitle) || expectedTitle.contains(candidateTitle) -> 70
                else -> 0
            }
            val artistScore = when {
                candidateArtist == expectedArtist -> 60
                candidateArtist.contains(expectedArtist) || expectedArtist.contains(candidateArtist) -> 45
                else -> 0
            }
            if (titleScore == 0 || artistScore == 0) null
            else candidate to (titleScore + artistScore + when {
                duration <= 0 || candidate.duration == null -> 0
                kotlin.math.abs(duration - candidate.duration) <= 2_000 -> 30
                kotlin.math.abs(duration - candidate.duration) <= 10_000 -> 20
                kotlin.math.abs(duration - candidate.duration) <= 30_000 -> 5
                else -> -20
            })
        }.filter { (_, score) -> score >= 120 }.maxByOrNull { (_, score) -> score }?.first
    }
}

private object LrcToLyricon {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("^\\s*\\[offset\\s*:\\s*(-?\\d+)\\s*]\\s*$", RegexOption.IGNORE_CASE)

    fun parse(lrc: String, durationMs: Long): List<RichLyricLine>? {
        val offsetMs = lrc.lineSequence().mapNotNull { offset.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }.firstOrNull() ?: 0
        val raw = buildList {
            lrc.lineSequence().forEach { line ->
                val matches = timestamp.findAll(line).toList()
                val text = matches.lastOrNull()?.let { line.substring(it.range.last + 1).trim() }.orEmpty()
                if (text.isBlank()) return@forEach
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) { 1 -> fraction.toLong() * 100; 2 -> fraction.toLong() * 10; 3 -> fraction.toLong(); else -> 0 }
                    add(RawLine((minutes * 60_000 + seconds * 1_000 + millis + offsetMs).coerceAtLeast(0), text))
                }
            }
        }.sortedBy(RawLine::begin).distinctBy { it.begin to it.text }
        if (raw.isEmpty()) return null
        return raw.mapIndexed { index, line ->
            val next = raw.getOrNull(index + 1)?.begin
            val end = maxOf(next ?: durationMs.takeIf { it > line.begin } ?: (line.begin + 5_000), line.begin + 1)
            RichLyricLine(begin = line.begin, end = end, duration = end - line.begin, text = line.text)
        }
    }

    private data class RawLine(val begin: Long, val text: String)
}
