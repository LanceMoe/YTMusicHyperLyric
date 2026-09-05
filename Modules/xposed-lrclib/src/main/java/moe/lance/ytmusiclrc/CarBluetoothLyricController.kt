package moe.lance.ytmusiclrc

import android.app.Application
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Controller injected into YouTube Music. Hooks MediaSession.setMetadata and setPlaybackState
 * to push synchronized lyrics to Car Bluetooth (AVRCP) via the active MediaSession.
 */
class CarBluetoothLyricController(
    private val module: XposedInterface,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = LatestLyricLookup("ytmusic-car-lyric")

    private val isInternalUpdate = ThreadLocal.withInitial { false }
    private val sequence = AtomicLong(0)
    private val initialized = AtomicBoolean(false)

    @Volatile private var activeSession: MediaSession? = null
    @Volatile private var currentKey: String? = null
    @Volatile private var bluetoothTracker: BluetoothStateTracker? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var application: Application? = null
    private var pendingZeroDurationRunnable: Runnable? = null
    // SharedPreferences implementations may retain listeners only weakly.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, _ ->
        mainHandler.post {
            val config = CarBluetoothLyricConfig.fromPreferences(preferences)
            ticker.setConfig(config)
            Log.i(LrclibXposedModule.TAG, "Car lyric config applied: $config")
        }
    }

    private val ticker = CarLyricTicker { newMetadata ->
        val session = activeSession ?: return@CarLyricTicker
        isInternalUpdate.set(true)
        try {
            session.setMetadata(newMetadata)
        } catch (error: Throwable) {
            Log.e(LrclibXposedModule.TAG, "Failed to publish synthetic metadata to MediaSession", error)
        } finally {
            isInternalUpdate.set(false)
        }
    }

    fun install(param: PackageLoadedParam) {
        runCatching {
            val appClass = param.defaultClassLoader.loadClass("android.app.Application")
            val onCreate = appClass.getDeclaredMethod("onCreate")
            module.deoptimize(onCreate)
            module.hook(onCreate).intercept(object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val result = chain.proceed()
                    (chain.thisObject as? Application)?.let(::initApplication)
                    return result
                }
            })
            Log.i(LrclibXposedModule.TAG, "Application.onCreate hook installed for YT Music")
        }.onFailure { error ->
            Log.e(LrclibXposedModule.TAG, "Could not install Application hook in YT Music", error)
        }

        runCatching {
            val mediaSessionClass = Class.forName("android.media.session.MediaSession", false, param.defaultClassLoader)
            val setMetadata = mediaSessionClass.getDeclaredMethod("setMetadata", MediaMetadata::class.java)
            val setPlaybackState = mediaSessionClass.getDeclaredMethod("setPlaybackState", PlaybackState::class.java)
            val release = mediaSessionClass.getDeclaredMethod("release")

            module.deoptimize(setMetadata)
            module.hook(setMetadata).intercept(SetMetadataHook())

            module.deoptimize(setPlaybackState)
            module.hook(setPlaybackState).intercept(SetPlaybackStateHook())

            module.deoptimize(release)
            module.hook(release).intercept(ReleaseHook())

            Log.i(LrclibXposedModule.TAG, "MediaSession hooks installed for YT Music")
        }.onFailure { error ->
            Log.e(LrclibXposedModule.TAG, "Could not install MediaSession hooks in YT Music", error)
        }
    }

    private fun initApplication(app: Application) {
        if (!initialized.compareAndSet(false, true)) return
        application = app
        moe.lance.ytmusiclrc.cache.LyricsCacheChanges.observe(app) {
            val session = activeSession
            val metadata = originalMetadata
            currentKey = null
            if (session != null) handleMetadata(session, metadata, allowNetwork = false)
        }
        Log.i(LrclibXposedModule.TAG, "Initializing CarBluetoothLyricController in YT Music (${app.packageName})")

        // Load config from remote preferences
        runCatching {
            prefs = module.getRemotePreferences(CarBluetoothLyricConfig.PREFS_NAME)
            prefs?.let { p ->
                val config = CarBluetoothLyricConfig.fromPreferences(p)
                ticker.setConfig(config)
                p.registerOnSharedPreferenceChangeListener(preferenceListener)
            }
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "Failed to load preferences from module, using defaults", error)
        }

        // Initialize Bluetooth state tracker
        val tracker = BluetoothStateTracker(app) { isConnected ->
            ticker.setBluetoothConnected(isConnected)
        }
        bluetoothTracker = tracker
        tracker.start()
        ticker.setBluetoothConnected(tracker.isBluetoothConnected)
    }

    private var originalMetadata: MediaMetadata? = null

    private inner class SetMetadataHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (isInternalUpdate.get() == true) return chain.proceed()
            val session = chain.thisObject as? MediaSession ?: return chain.proceed()
            val metadata = chain.args.firstOrNull() as? MediaMetadata
            val result = chain.proceed()
            mainHandler.post { handleMetadata(session, metadata) }
            return result
        }
    }

    private fun handleMetadata(session: MediaSession, metadata: MediaMetadata?, allowNetwork: Boolean = true) {
        if (session != activeSession) currentKey = null
        activeSession = session
        originalMetadata = metadata

        if (metadata == null) {
            sequence.incrementAndGet()
            executor.cancel()
            pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
            pendingZeroDurationRunnable = null
            currentKey = null
            ticker.setSong(null, null)
            return
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)

        if (title.isBlank() || artist.isBlank()) {
            sequence.incrementAndGet()
            currentKey = null
            executor.cancel()
            pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
            pendingZeroDurationRunnable = null
            ticker.setSong(metadata, null)
            return
        }

        val key = "$title\u0000$artist\u0000$album\u0000$duration"
        if (key == currentKey) {
            ticker.updateMetadata(metadata)
            return
        }
        currentKey = key

        executor.cancel()
        pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
        pendingZeroDurationRunnable = null

        val requestId = sequence.incrementAndGet()

        // Reset ticker to original metadata while fetching
        ticker.setSong(metadata, null)

        fun dispatchLookup() {
            executor.execute {
                val lines = runCatching {
                    LyricsRepository.getLyrics(title, artist, album, duration, application, allowNetwork)
                }.onFailure { error ->
                    Log.w(LrclibXposedModule.TAG, "Car lyric lookup failed for '$title' — '$artist'", error)
                }.getOrNull()

                mainHandler.post {
                    if (requestId == sequence.get() && key == currentKey && session == activeSession) {
                        if (!lines.isNullOrEmpty()) {
                            Log.i(
                                LrclibXposedModule.TAG,
                                "Loaded ${lines.size} car lyric lines for: $title — $artist",
                            )
                            ticker.setSong(metadata, lines)
                        } else {
                            Log.i(LrclibXposedModule.TAG, "No car lyrics found for: $title — $artist")
                            ticker.setSong(metadata, null)
                        }
                    }
                }
            }
        }

        if (duration <= 0L) {
            val delayedTask = Runnable {
                pendingZeroDurationRunnable = null
                if (requestId == sequence.get() && key == currentKey && session == activeSession) {
                    dispatchLookup()
                }
            }
            pendingZeroDurationRunnable = delayedTask
            mainHandler.postDelayed(delayedTask, 350L)
        } else {
            dispatchLookup()
        }
    }

    private inner class SetPlaybackStateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val session = chain.thisObject as? MediaSession
            val state = chain.args.firstOrNull() as? PlaybackState
            val result = chain.proceed()
            mainHandler.post {
                if (activeSession == null) activeSession = session
                if (session == activeSession) ticker.updatePlaybackState(state)
            }
            return result
        }
    }

    private inner class ReleaseHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val session = chain.thisObject as? MediaSession
            val result = chain.proceed()
            mainHandler.post {
                if (session == activeSession) {
                    sequence.incrementAndGet()
                    currentKey = null
                    executor.cancel()
                    pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
                    pendingZeroDurationRunnable = null
                    activeSession = null
                    originalMetadata = null
                    ticker.updatePlaybackState(null)
                    ticker.setSong(null, null)
                }
            }
            return result
        }
    }
}
