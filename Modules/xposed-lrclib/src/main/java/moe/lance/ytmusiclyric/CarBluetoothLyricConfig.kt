package moe.lance.ytmusiclyric

import android.content.SharedPreferences

enum class LyricDisplayMode(val key: String, val displayName: String) {
    TITLE_ONLY("title_only", "标题栏替换 (推荐)"),
    TITLE_WITH_SONG("title_with_song", "标题栏拼接 (歌名 - 歌词)"),
    ARTIST_ONLY("artist_only", "歌手栏替换"),
    ALBUM_ONLY("album_only", "专辑栏替换");

    companion object {
        fun fromKey(key: String?): LyricDisplayMode {
            return entries.firstOrNull { it.key == key } ?: TITLE_ONLY
        }
    }
}

data class CarBluetoothLyricConfig(
    val enabled: Boolean = true,
    val onlyWhenBluetooth: Boolean = true,
    val displayMode: LyricDisplayMode = LyricDisplayMode.TITLE_ONLY,
    val offsetMs: Long = 0L,
) {
    companion object {
        const val PREFS_NAME = "car_lyric_prefs"
        const val KEY_ENABLED = "pref_enabled"
        const val KEY_ONLY_BLUETOOTH = "pref_only_bluetooth"
        const val KEY_DISPLAY_MODE = "pref_display_mode"
        const val KEY_OFFSET_MS = "pref_offset_ms"
        const val KEY_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"

        fun fromPreferences(prefs: SharedPreferences?): CarBluetoothLyricConfig {
            if (prefs == null) return CarBluetoothLyricConfig()
            return runCatching {
                CarBluetoothLyricConfig(
                    enabled = prefs.getBoolean(KEY_ENABLED, true),
                    onlyWhenBluetooth = prefs.getBoolean(KEY_ONLY_BLUETOOTH, true),
                    displayMode = LyricDisplayMode.fromKey(prefs.getString(KEY_DISPLAY_MODE, LyricDisplayMode.TITLE_ONLY.key)),
                    offsetMs = prefs.getLong(KEY_OFFSET_MS, 0L),
                )
            }.getOrDefault(CarBluetoothLyricConfig())
        }
    }
}
