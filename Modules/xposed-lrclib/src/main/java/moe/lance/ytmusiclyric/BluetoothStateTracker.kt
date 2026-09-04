package moe.lance.ytmusiclyric

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Monitors Bluetooth audio connection state (A2DP / BLE Audio / SCO) in real-time.
 */
class BluetoothStateTracker(
    context: Context,
    private val onStateChanged: (isConnected: Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var isBluetoothConnected: Boolean = false
        private set

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            checkAndUpdate()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            checkAndUpdate()
        }
    }

    fun start() {
        checkAndUpdate()
        runCatching {
            audioManager?.registerAudioDeviceCallback(callback, handler)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "Could not register AudioDeviceCallback", error)
        }
    }

    fun stop() {
        runCatching {
            audioManager?.unregisterAudioDeviceCallback(callback)
        }
    }

    fun checkAndUpdate(): Boolean {
        val connected = checkBluetoothAudioConnected()
        if (connected != isBluetoothConnected) {
            isBluetoothConnected = connected
            Log.i(LrclibXposedModule.TAG, "Bluetooth audio connection state changed: isConnected=$connected")
            onStateChanged(connected)
        }
        return connected
    }

    private fun checkBluetoothAudioConnected(): Boolean {
        val manager = audioManager ?: return false
        val devices = runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull().orEmpty()

        val hasBtDevice = devices.any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    } else false
                }
            }
        }
        if (hasBtDevice) return true

        @Suppress("DEPRECATION")
        return runCatching { manager.isBluetoothA2dpOn }.getOrDefault(false)
    }
}
