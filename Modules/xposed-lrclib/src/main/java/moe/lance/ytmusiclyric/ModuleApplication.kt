package moe.lance.ytmusiclyric

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** Publishes locally saved settings to the framework store read by hooked apps. */
class ModuleApplication : Application() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val services = mutableSetOf<XposedService>()
    private lateinit var preferences: SharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mainHandler.post { services.forEach(::publishConfig) }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = getSharedPreferences(CarBluetoothLyricConfig.PREFS_NAME, MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                mainHandler.post {
                    services.add(service)
                    // Also migrates settings saved before remote synchronization existed.
                    publishConfig(service)
                }
            }

            override fun onServiceDied(service: XposedService) {
                mainHandler.post { services.remove(service) }
            }
        })
    }

    private fun publishConfig(service: XposedService) {
        runCatching {
            val config = CarBluetoothLyricConfig.fromPreferences(preferences)
            service.getRemotePreferences(CarBluetoothLyricConfig.PREFS_NAME).edit()
                .putBoolean(CarBluetoothLyricConfig.KEY_ENABLED, config.enabled)
                .putBoolean(CarBluetoothLyricConfig.KEY_ONLY_BLUETOOTH, config.onlyWhenBluetooth)
                .putString(CarBluetoothLyricConfig.KEY_DISPLAY_MODE, config.displayMode.key)
                .putLong(CarBluetoothLyricConfig.KEY_OFFSET_MS, config.offsetMs)
                .apply()
            Log.i("YTMusicLrc", "Car lyric config synchronized: $config")
        }.onFailure { error ->
            Log.e("YTMusicLrc", "Failed to synchronize car lyric config", error)
        }
    }
}
