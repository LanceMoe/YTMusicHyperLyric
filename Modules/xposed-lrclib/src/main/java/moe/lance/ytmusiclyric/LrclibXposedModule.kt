package moe.lance.ytmusiclyric

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A conventional LSPosed module. It repairs HyperLyric's ZIP loading boundary on Android/HyperOS
 * and publishes synchronized lyrics via Lyricon Provider to HyperLyric.
 */
class LrclibXposedModule : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Log.i(TAG, "LSPosed module loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (Application.getProcessName().contains(':')) return

        when {
            param.packageName == SYSTEM_UI -> initSystemUiHook(param)
            isYouTubeMusic(param.packageName) -> initYouTubeMusicHook(param)
        }
    }

    private fun isYouTubeMusic(packageName: String): Boolean {
        return packageName == YT_MUSIC || packageName.endsWith(".youtube.music")
    }

    private fun initYouTubeMusicHook(param: PackageLoadedParam) {
        Log.i(TAG, "Initializing YouTube Music Car Bluetooth Lyric Hook for ${param.packageName}")
        CarBluetoothLyricController(this).install(param)
    }

    private fun initSystemUiHook(param: PackageLoadedParam) {
        installPluginDexReadOnlyHook(param)
        runCatching {
            val appClass = param.defaultClassLoader.loadClass("android.app.Application")
            val onCreate = appClass.getDeclaredMethod("onCreate")
            deoptimize(onCreate)
            hook(onCreate).intercept(ApplicationCreateHook())
            Log.i(TAG, "Application.onCreate hook installed for SystemUI")
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
                    "active=${provider.service.isActive}; waiting for connection",
            )
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
        const val MODULE_PACKAGE = "moe.lance.ytmusiclyric"
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
        Thread(runnable, "ytmusic-lyrics").apply { isDaemon = true }
    }
    private val sequence = AtomicLong(0)

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

        val key = "$title\u0000$artist\u0000$album\u0000$duration"
        if (key == currentKey) return
        currentKey = key
        val request = sequence.incrementAndGet()
        provider.player.setSong(null)

        executor.execute {
            val lines = runCatching {
                LyricsRepository.getLyrics(title, artist, album, duration, app)
            }.onFailure { error ->
                Log.w(LrclibXposedModule.TAG, "Lyric lookup failed for '$title' — '$artist'", error)
            }.getOrNull()

            if (lines.isNullOrEmpty()) {
                Log.i(LrclibXposedModule.TAG, "No timed lyrics found across providers: $title — $artist")
                return@execute
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
            Song(id = key, name = title, artist = artist, duration = duration, lyrics = lines),
        )
        controller.playbackState?.let(::publishPlayback)
        Log.i(
            LrclibXposedModule.TAG,
            "Published ${lines.size} lyric lines: $title — $artist; " +
                "accepted=$accepted active=${provider.service.isActive}",
        )
    }

    private fun publishPlayback(state: PlaybackState) {
        provider.player.setPlaybackState(state)
    }
}
