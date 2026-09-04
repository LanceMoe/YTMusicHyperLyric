package moe.lance.ytmusiclyric

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ytmusic-car-lyric").apply { isDaemon = true }
    }

    private val isInternalUpdate = ThreadLocal.withInitial { false }
    private val sequence = AtomicLong(0)
    private val initialized = AtomicBoolean(false)

    @Volatile private var activeSession: MediaSession? = null
    @Volatile private var currentKey: String? = null
    @Volatile private var bluetoothTracker: BluetoothStateTracker? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var application: Application? = null
    private var pendingZeroDurationRunnable: Runnable? = null

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
        Log.i(LrclibXposedModule.TAG, "Initializing CarBluetoothLyricController in YT Music (${app.packageName})")

        // Load config from remote preferences
        runCatching {
            prefs = module.getRemotePreferences(CarBluetoothLyricConfig.PREFS_NAME)
            prefs?.let { p ->
                val config = CarBluetoothLyricConfig.fromPreferences(p)
                ticker.setConfig(config)
                p.registerOnSharedPreferenceChangeListener { sharedPreferences, _ ->
                    ticker.setConfig(CarBluetoothLyricConfig.fromPreferences(sharedPreferences))
                }
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

    private inner class SetMetadataHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (isInternalUpdate.get() == true) {
                return chain.proceed()
            }

            val session = chain.thisObject as? MediaSession
            val metadata = chain.args.firstOrNull() as? MediaMetadata
            activeSession = session

            if (metadata == null) {
                pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
                pendingZeroDurationRunnable = null
                currentKey = null
                ticker.setSong(null, null)
                return chain.proceed()
            }

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)

            if (title.isBlank() || artist.isBlank()) {
                pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
                pendingZeroDurationRunnable = null
                ticker.setSong(metadata, null)
                return chain.proceed()
            }

            val key = "$title\u0000$artist\u0000$album\u0000$duration"
            if (key == currentKey) {
                return chain.proceed()
            }
            currentKey = key

            pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
            pendingZeroDurationRunnable = null

            val requestId = sequence.incrementAndGet()

            // Reset ticker to original metadata while fetching
            ticker.setSong(metadata, null)

            fun dispatchLookup() {
                executor.execute {
                    val lines = runCatching {
                        LyricsRepository.getLyrics(title, artist, album, duration, application)
                    }.onFailure { error ->
                        Log.w(LrclibXposedModule.TAG, "Car lyric lookup failed for '$title' — '$artist'", error)
                    }.getOrNull()

                    mainHandler.post {
                        if (requestId == sequence.get() && key == currentKey) {
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
                    if (requestId == sequence.get() && key == currentKey) {
                        dispatchLookup()
                    }
                }
                pendingZeroDurationRunnable = delayedTask
                mainHandler.postDelayed(delayedTask, 350L)
            } else {
                dispatchLookup()
            }

            return chain.proceed()
        }
    }

    private inner class SetPlaybackStateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val session = chain.thisObject as? MediaSession
            val state = chain.args.firstOrNull() as? PlaybackState
            if (session != null) {
                activeSession = session
            }
            ticker.updatePlaybackState(state)
            return chain.proceed()
        }
    }

    private inner class ReleaseHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val session = chain.thisObject as? MediaSession
            if (session == activeSession) {
                pendingZeroDurationRunnable?.let(mainHandler::removeCallbacks)
                pendingZeroDurationRunnable = null
                ticker.stop()
                activeSession = null
            }
            return chain.proceed()
        }
    }
}
