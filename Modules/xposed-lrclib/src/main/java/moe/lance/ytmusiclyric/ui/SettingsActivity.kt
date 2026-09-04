package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import moe.lance.ytmusiclyric.CarBluetoothLyricConfig
import moe.lance.ytmusiclyric.CarLyricTicker
import moe.lance.ytmusiclyric.LyricDisplayMode
import moe.lance.ytmusiclyric.LyricsRepository
import moe.lance.ytmusiclyric.R
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import moe.lance.ytmusiclyric.cache.LyricsDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SettingsActivity : Activity() {
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var dbHelper: LyricsDatabaseHelper
    private lateinit var ui: HyperStyle
    private lateinit var cacheStatsText: TextView
    private lateinit var cacheListContainer: LinearLayout
    private var currentSearchKeyword = ""
    private var cacheRequest = 0
    private var selectedTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = LyricsDatabaseHelper.getInstance(this)
        ui = HyperStyle(this)
        val prefs = getSharedPreferences(CarBluetoothLyricConfig.PREFS_NAME, Context.MODE_PRIVATE)
        var config = CarBluetoothLyricConfig.fromPreferences(prefs)
        fun saveConfig(value: CarBluetoothLyricConfig) {
            config = value
            prefs.edit()
                .putBoolean(CarBluetoothLyricConfig.KEY_ENABLED, value.enabled)
                .putBoolean(CarBluetoothLyricConfig.KEY_ONLY_BLUETOOTH, value.onlyWhenBluetooth)
                .putString(CarBluetoothLyricConfig.KEY_DISPLAY_MODE, value.displayMode.key)
                .putLong(CarBluetoothLyricConfig.KEY_OFFSET_MS, value.offsetMs)
                .apply()
        }

        selectedTab = savedInstanceState?.getInt("selectedTab", 0) ?: 0
        val pages = ui.tabbedPage(
            listOf(
                HyperStyle.Tab("设置", R.id.settings_scroll, R.id.settings_tab, R.drawable.ic_nav_settings),
                HyperStyle.Tab("歌词缓存", R.id.cache_scroll, R.id.cache_tab, R.drawable.ic_nav_music),
            ),
            initialIndex = selectedTab,
        ) { index ->
            selectedTab = index
            if (index == 1) loadCacheList()
        }
        val content = pages[0]
        setupCacheTab(pages[1])
        val car = ui.section(content, "车载蓝牙歌词")
        ui.toggle(car, "显示车载歌词", "将同步歌词推送到车机的歌曲信息栏", config.enabled) {
            saveConfig(config.copy(enabled = it))
        }
        ui.divider(car)
        ui.toggle(car, "仅在连接蓝牙时生效", "暂停或断开蓝牙后恢复原歌名，避免影响手机通知栏与锁屏", config.onlyWhenBluetooth) {
            saveConfig(config.copy(onlyWhenBluetooth = it))
        }
        ui.divider(car)
        val modes = listOf(
            LyricDisplayMode.TITLE_ONLY to "标题栏替换（推荐）",
            LyricDisplayMode.TITLE_WITH_SONG to "标题栏拼接 · 歌名 - 歌词",
            LyricDisplayMode.ARTIST_ONLY to "歌手栏替换 · 保留歌名",
            LyricDisplayMode.ALBUM_ONLY to "专辑栏替换 · 保留歌名与歌手",
        )
        val modeRow = ui.preference(car, "歌词显示位置", "选择车机用于展示歌词的信息栏")
        val modeValue = ui.label(modes.first { it.first == config.displayMode }.second, 14f, ui.primary)
        val modeCopy = modeRow.getChildAt(0) as LinearLayout
        modeCopy.addView(modeValue.apply { setPadding(0, ui.dp(8), 0, 0) })
        modeRow.addView(ui.label("›", 26f, ui.secondary))
        modeRow.background = ui.ripple(ui.surface)
        modeRow.setOnClickListener {
            ui.showDialog(AlertDialog.Builder(this)
                .setTitle("歌词显示位置")
                .setSingleChoiceItems(modes.map { it.second }.toTypedArray(), modes.indexOfFirst { it.first == config.displayMode }) { dialog, index ->
                    saveConfig(config.copy(displayMode = modes[index].first))
                    modeValue.text = modes[index].second
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null))
        }
        ui.divider(car)
        val offset = ui.paddedContent(car)
        offset.addView(ui.label("蓝牙延迟补偿"))
        ui.hint(offset, "根据车机的音频延迟微调，范围 −5000 至 +5000 ms")
        val offsetValue = ui.label("${config.offsetMs} ms", 28f, ui.primary, true)
        offsetValue.gravity = android.view.Gravity.CENTER
        offsetValue.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        offset.addView(offsetValue, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        fun updateOffset(value: Long) {
            saveConfig(config.copy(offsetMs = value.coerceIn(-5000L, 5000L)))
            offsetValue.text = "${config.offsetMs} ms"
        }
        val offsetButtons = ui.row()
        addEqualButton(offsetButtons, ui.button("−200 ms") { updateOffset(config.offsetMs - 200) })
        addEqualButton(offsetButtons, ui.button("重置") { updateOffset(0) })
        addEqualButton(offsetButtons, ui.button("+200 ms") { updateOffset(config.offsetMs + 200) }, last = true)
        offset.addView(offsetButtons)

        val system = ui.section(content, "模块与系统")
        ui.toggle(system, "隐藏桌面图标", "隐藏后仍可从 LSPosed 的模块设置进入", prefs.getBoolean(CarBluetoothLyricConfig.KEY_HIDE_LAUNCHER_ICON, false)) { hide ->
            prefs.edit().putBoolean(CarBluetoothLyricConfig.KEY_HIDE_LAUNCHER_ICON, hide).apply()
            setLauncherIconVisible(!hide)
            Toast.makeText(this, if (hide) "桌面图标已隐藏，可从 LSPosed 模块设置重新打开" else "桌面图标已显示", Toast.LENGTH_SHORT).show()
        }
        ui.divider(system)
        val restartContent = ui.paddedContent(system)
        restartContent.addView(ui.label("重启系统界面"))
        val restartStatus = ui.hint(restartContent, "需要 root 授权，重启期间状态栏和导航栏会短暂消失。")
        lateinit var restartButton: Button
        restartButton = ui.button("重启 SystemUI") { restartSystemUi(restartButton, restartStatus) }
        restartContent.addView(restartButton, LinearLayout.LayoutParams(-1, -2))

        val test = ui.paddedContent(ui.section(content, "歌词检索测试"))
        test.addView(ui.label("试试三源检索", 18f, bold = true))
        ui.hint(test, "依次检索 LRCLIB、网易云与酷狗，预览车机上的歌词显示效果。")
        test.addView(ui.label("歌曲标题", 13f, ui.secondary).apply { setPadding(0, 0, 0, ui.dp(6)) })
        val titleInput = ui.input("歌曲标题", "晴天").apply { id = R.id.test_title }
        test.addView(titleInput)
        test.addView(ui.label("歌手", 13f, ui.secondary).apply { setPadding(0, 0, 0, ui.dp(6)) })
        val artistInput = ui.input("歌手（可留空）", "周杰伦").apply { id = R.id.test_artist }
        test.addView(artistInput)
        val resultText = ui.label("输入歌曲信息，开始检索同步歌词。", 13f, ui.secondary).apply {
            id = R.id.test_result
            freezesText = true
            setPadding(0, ui.dp(12), 0, 0)
            setTextIsSelectable(true)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        lateinit var testButton: Button
        testButton = ui.button("开始检索", prominent = true) {
            val title = titleInput.text.toString().trim()
            val artist = artistInput.text.toString().trim()
            if (title.isBlank()) {
                titleInput.error = "请输入歌曲标题"
            } else {
                resultText.setTextColor(ui.secondary)
                resultText.text = "正在联网检索，请稍候…"
                testButton.isEnabled = false
                testButton.text = "检索中…"
                val mode = config.displayMode
                bgExecutor.execute {
                    val result = runCatching { LyricsRepository.getLyrics(title, artist, "", 240_000L, this) }
                    mainHandler.post {
                        if (isFinishing || isDestroyed) return@post
                        testButton.isEnabled = true
                        testButton.text = "开始检索"
                        val lines = result.getOrNull()
                        if (lines.isNullOrEmpty()) {
                            resultText.setTextColor(ui.error)
                            resultText.text = "未检索到同步歌词，请检查歌名、歌手或网络后重试。"
                        } else {
                            val sample = lines.getOrNull(1) ?: lines.first()
                            val (formattedTitle, formattedArtist, formattedAlbum) = CarLyricTicker.formatMetadata(
                                origTitle = title, origArtist = artist, origAlbum = "测试专辑", activeLine = sample, mode = mode,
                            )
                            val preview = lines.take(3).joinToString("\n") { "[${it.begin}ms] ${it.text}" }
                            resultText.setTextColor(ui.text)
                            resultText.text = "已匹配 ${lines.size} 行同步歌词，已保存至本地缓存。\n\n车机预览 · ${mode.displayName}\n标题：$formattedTitle\n歌手：$formattedArtist\n专辑：$formattedAlbum\n\n歌词前 3 句\n$preview"
                        }
                        loadCacheList()
                    }
                }
            }
        }
        test.addView(testButton, LinearLayout.LayoutParams(-1, -2))
        test.addView(resultText)

        val scope = ui.section(content, "使用指引")
        ui.preference(scope, "System UI", "在 LSPosed 勾选「系统界面」，用于 HyperLyric 状态栏与超级岛歌词。")
        ui.divider(scope)
        ui.preference(scope, "YouTube Music", "勾选 YouTube Music，用于车载蓝牙歌词。两个作用域可同时启用。")
        content.addView(ui.label("YouTube Music HyperLyric · 歌词增强模块", 12f, ui.secondary).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(ui.dp(12), ui.dp(28), ui.dp(12), 0)
        })
    }

    private fun setupCacheTab(content: LinearLayout) {
        val cacheContent = ui.paddedContent(ui.section(content, "本地歌词"))
        ui.hint(cacheContent, "点击歌曲可编辑歌词、手动搜索或重新下载。下载失败的歌曲也会保留记录。")
        cacheStatsText = ui.label("正在统计缓存…", 13f, ui.secondary)
        cacheContent.addView(cacheStatsText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        val search = ui.input("搜索歌名或歌手").apply { id = R.id.cache_search }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchKeyword = s?.toString().orEmpty().trim()
                loadCacheList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        cacheContent.addView(search)
        cacheContent.addView(ui.button("清空全部缓存", destructive = true) { handleClearAllCache() }, LinearLayout.LayoutParams(-1, -2))
        cacheListContainer = ui.column()
        cacheContent.addView(cacheListContainer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selectedTab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun addEqualButton(row: LinearLayout, button: Button, last: Boolean = false) {
        row.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { if (!last) marginEnd = ui.dp(8) })
    }

    override fun onResume() {
        super.onResume()
        loadCacheList()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) loadCacheList()
    }

    private fun loadCacheList() {
        if (!::cacheListContainer.isInitialized || isDestroyed) return
        val keyword = currentSearchKeyword.takeIf { it.isNotBlank() }
        val request = ++cacheRequest
        bgExecutor.execute {
            val count = dbHelper.getCount(keyword)
            val list = dbHelper.getAll(searchKeyword = keyword, limit = 100)
            mainHandler.post {
                if (isFinishing || isDestroyed || request != cacheRequest) return@post
                cacheStatsText.text = if (keyword == null) "共 $count 首歌曲（含下载失败）" else "找到 $count 首歌曲"
                if (count > list.size) cacheStatsText.append(" · 显示前 ${list.size} 首，请搜索缩小范围")
                cacheListContainer.removeAllViews()
                if (list.isEmpty()) {
                    cacheListContainer.addView(ui.label(
                        if (keyword == null) "暂无歌曲记录\n播放 YouTube Music 并检索歌词后，会自动保存在这里。" else "没有匹配的歌曲\n试试其他歌名或歌手。",
                        14f, ui.secondary,
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                        setPadding(ui.dp(8), ui.dp(24), ui.dp(8), ui.dp(24))
                    })
                } else list.forEach { addCacheEntry(it) }
            }
        }
    }

    private fun addCacheEntry(entry: LyricsCacheEntry) {
        val row = ui.row().apply {
            setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14))
            background = ui.ripple(ui.field, 14)
            isFocusable = true
            setOnClickListener { LyricDetailActivity.start(this@SettingsActivity, entry.cacheKey, 1001) }
        }
        val info = ui.column()
        info.addView(ui.label(entry.title, 16f, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        info.addView(ui.label(entry.artist, 13f, ui.secondary).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, ui.dp(3), 0, ui.dp(8))
        })
        info.addView(ui.badge(entry.displaySource, !entry.hasLyrics), LinearLayout.LayoutParams(-2, -2))
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.updatedAt))
        info.addView(ui.label("更新于 $date", 11f, ui.secondary).apply { setPadding(0, ui.dp(6), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ui.button("删除", destructive = true) { confirmDeleteSingle(entry) }.apply {
            contentDescription = "删除《${entry.title}》的缓存"
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
        cacheListContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    }

    private fun confirmDeleteSingle(entry: LyricsCacheEntry) {
        ui.showDialog(AlertDialog.Builder(this)
            .setTitle("删除缓存")
            .setMessage("确定删除《${entry.title} - ${entry.artist}》的本地歌词缓存吗？")
            .setPositiveButton("删除") { _, _ ->
                dbHelper.delete(entry.cacheKey)
                LyricsRepository.evictFromMemory(entry.cacheKey)
                Toast.makeText(this, "已删除该歌曲缓存", Toast.LENGTH_SHORT).show()
                loadCacheList()
            }
            .setNegativeButton("取消", null))
    }

    private fun handleClearAllCache() {
        ui.showDialog(AlertDialog.Builder(this)
            .setTitle("清空全部缓存")
            .setMessage("确定要清空本地全部歌词缓存吗？清空后所有歌曲将需要重新联网检索。")
            .setPositiveButton("全部清空") { _, _ ->
                val deleted = dbHelper.deleteAll()
                LyricsRepository.clearMemoryCache()
                Toast.makeText(this, "已清空 $deleted 条本地歌词缓存", Toast.LENGTH_SHORT).show()
                loadCacheList()
            }
            .setNegativeButton("取消", null))
    }

    private fun setLauncherIconVisible(visible: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "$packageName.ui.SettingsLauncher"),
            if (visible) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
    }

    private fun restartSystemUi(button: Button, status: TextView) {
        button.isEnabled = false
        status.setTextColor(ui.secondary)
        status.text = "正在请求 root 并重启 SystemUI…"
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
                button.isEnabled = true
                status.setTextColor(if (result.isSuccess) ui.success else ui.error)
                status.text = if (result.isSuccess) "SystemUI 重启命令已执行，状态栏通常会在几秒内恢复。"
                else "重启失败：${result.exceptionOrNull()?.message ?: "未获得 root 权限"}"
            }
        }
    }

    override fun onDestroy() {
        bgExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
