package moe.lance.ytmusiclyric.ui

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import moe.lance.ytmusiclyric.CarBluetoothLyricConfig
import moe.lance.ytmusiclyric.CarLyricTicker
import moe.lance.ytmusiclyric.LyricDisplayMode
import moe.lance.ytmusiclyric.LyricsRepository
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import moe.lance.ytmusiclyric.cache.LyricsDatabaseHelper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SettingsActivity : ComponentActivity() {
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var dbHelper: LyricsDatabaseHelper
    private lateinit var prefs: android.content.SharedPreferences
    private var cacheRequest = 0
    private var uiState by mutableStateOf<SettingsUiState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        dbHelper = LyricsDatabaseHelper.getInstance(this)
        prefs = getSharedPreferences(CarBluetoothLyricConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val initialTab = savedInstanceState?.getInt(KEY_SELECTED_TAB, 0) ?: 0
        uiState = SettingsUiState(
            config = CarBluetoothLyricConfig.fromPreferences(prefs),
            selectedTab = initialTab,
            launcherIconHidden = prefs.getBoolean(CarBluetoothLyricConfig.KEY_HIDE_LAUNCHER_ICON, false),
        )

        setContent {
            val state = uiState ?: return@setContent
            MiuixSettingsScreen(
                state = state,
                onTabSelected = { tab -> updateState { copy(selectedTab = tab) } },
                onConfigChanged = ::saveConfig,
                onModeClick = { updateState { copy(showModeDialog = true) } },
                onDismissModeDialog = { updateState { copy(showModeDialog = false) } },
                onSelectMode = ::handleSelectMode,
                onLauncherIconChanged = ::setLauncherIconHidden,
                onRestartSystemUi = ::restartSystemUi,
                onCacheSearchChanged = { keyword ->
                    updateState { copy(cacheSearch = keyword) }
                    loadCacheList()
                },
                onClearCache = { updateState { copy(showClearAllDialog = true) } },
                onDismissClearAll = { updateState { copy(showClearAllDialog = false) } },
                onConfirmClearAll = ::handleConfirmClearAll,
                onCacheClick = { entry -> LyricDetailActivity.start(this, entry.cacheKey, REQUEST_DETAIL) },
                onDeleteCache = { entry -> updateState { copy(pendingDeleteEntry = entry) } },
                onDismissDeleteSingle = { updateState { copy(pendingDeleteEntry = null) } },
                onConfirmDeleteSingle = ::handleConfirmDeleteSingle,
                onTestSearch = ::runTestSearch,
            )
        }
        loadCacheList()
    }

    private fun updateState(transform: SettingsUiState.() -> SettingsUiState) {
        uiState = uiState?.transform()
    }

    private fun saveConfig(value: CarBluetoothLyricConfig) {
        prefs.edit()
            .putBoolean(CarBluetoothLyricConfig.KEY_ENABLED, value.enabled)
            .putBoolean(CarBluetoothLyricConfig.KEY_ONLY_BLUETOOTH, value.onlyWhenBluetooth)
            .putString(CarBluetoothLyricConfig.KEY_DISPLAY_MODE, value.displayMode.key)
            .putLong(CarBluetoothLyricConfig.KEY_OFFSET_MS, value.offsetMs)
            .apply()
        updateState { copy(config = value) }
    }

    private fun handleSelectMode(mode: LyricDisplayMode) {
        saveConfig((uiState?.config ?: CarBluetoothLyricConfig()).copy(displayMode = mode))
        updateState { copy(showModeDialog = false) }
    }

    private fun setLauncherIconHidden(hidden: Boolean) {
        prefs.edit().putBoolean(CarBluetoothLyricConfig.KEY_HIDE_LAUNCHER_ICON, hidden).apply()
        setLauncherIconVisible(!hidden)
        updateState { copy(launcherIconHidden = hidden) }
        Toast.makeText(
            this,
            if (hidden) "桌面图标已隐藏，可从 LSPosed 模块设置重新打开" else "桌面图标已显示",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun setLauncherIconVisible(visible: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "$packageName.ui.SettingsLauncher"),
            if (visible) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
    }

    private fun restartSystemUi() {
        updateState { copy(restartLoading = true, restartStatus = "正在请求 root 并重启 SystemUI…") }
        bgExecutor.execute {
            val result = runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall -TERM com.android.systemui"))
                if (!process.waitFor(8L, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("root 命令超时")
                }
                if (process.exitValue() != 0) error("root 命令返回 ${process.exitValue()}")
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                updateState {
                    copy(
                        restartLoading = false,
                        restartStatus = if (result.isSuccess) {
                            "SystemUI 重启命令已执行，状态栏通常会在几秒内恢复。"
                        } else {
                            "重启失败：${result.exceptionOrNull()?.message ?: "未获得 root 权限"}"
                        },
                    )
                }
            }
        }
    }

    private fun runTestSearch(title: String, artist: String) {
        if (title.isBlank()) {
            Toast.makeText(this, "请输入歌曲标题", Toast.LENGTH_SHORT).show()
            return
        }
        val mode = uiState?.config?.displayMode ?: CarBluetoothLyricConfig().displayMode
        updateState { copy(testLoading = true, testError = false, testResult = "正在联网检索，请稍候…") }
        bgExecutor.execute {
            val result = runCatching { LyricsRepository.getLyrics(title, artist, "", 240_000L, this) }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                val lines = result.getOrNull()
                if (lines.isNullOrEmpty()) {
                    updateState {
                        copy(
                            testLoading = false,
                            testError = true,
                            testResult = "未检索到同步歌词，请检查歌名、歌手或网络后重试。",
                        )
                    }
                } else {
                    val sample = lines.getOrNull(1) ?: lines.first()
                    val (formattedTitle, formattedArtist, formattedAlbum) = CarLyricTicker.formatMetadata(
                        origTitle = title,
                        origArtist = artist,
                        origAlbum = "测试专辑",
                        activeLine = sample,
                        mode = mode,
                    )
                    val preview = lines.take(3).joinToString("\n") { "[${it.begin}ms] ${it.text}" }
                    updateState {
                        copy(
                            testLoading = false,
                            testError = false,
                            testResult = "已匹配 ${lines.size} 行同步歌词，已保存至本地缓存。\n\n车机预览 · ${mode.displayName}\n标题：$formattedTitle\n歌手：$formattedArtist\n专辑：$formattedAlbum\n\n歌词前 3 句\n$preview",
                        )
                    }
                    loadCacheList()
                }
            }
        }
    }

    private fun loadCacheList() {
        if (isDestroyed) return
        val keyword = uiState?.cacheSearch?.trim()?.takeIf { it.isNotBlank() }
        val request = ++cacheRequest
        bgExecutor.execute {
            val count = dbHelper.getCount(keyword)
            val list = dbHelper.getAll(searchKeyword = keyword, limit = 100)
            mainHandler.post {
                if (isFinishing || isDestroyed || request != cacheRequest) return@post
                updateState { copy(cacheEntries = list, cacheCount = count) }
            }
        }
    }

    private fun handleConfirmDeleteSingle(entry: LyricsCacheEntry) {
        dbHelper.delete(entry.cacheKey)
        LyricsRepository.evictFromMemory(entry.cacheKey)
        Toast.makeText(this, "已删除该歌曲缓存", Toast.LENGTH_SHORT).show()
        updateState { copy(pendingDeleteEntry = null) }
        loadCacheList()
    }

    private fun handleConfirmClearAll() {
        val deleted = dbHelper.deleteAll()
        LyricsRepository.clearMemoryCache()
        Toast.makeText(this, "已清空 $deleted 条本地歌词缓存", Toast.LENGTH_SHORT).show()
        updateState { copy(showClearAllDialog = false) }
        loadCacheList()
    }

    override fun onResume() {
        super.onResume()
        if (::dbHelper.isInitialized) loadCacheList()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, uiState?.selectedTab ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DETAIL && resultCode == RESULT_OK) loadCacheList()
    }

    override fun onDestroy() {
        bgExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selectedTab"
        private const val REQUEST_DETAIL = 1001
    }
}
