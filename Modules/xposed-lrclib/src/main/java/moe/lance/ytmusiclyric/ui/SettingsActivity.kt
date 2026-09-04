package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import moe.lance.ytmusiclyric.CarBluetoothLyricConfig
import moe.lance.ytmusiclyric.CarLyricTicker
import moe.lance.ytmusiclyric.LyricDisplayMode
import moe.lance.ytmusiclyric.LyricsRepository
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import moe.lance.ytmusiclyric.cache.LyricsDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class SettingsActivity : Activity() {

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var dbHelper: LyricsDatabaseHelper

    // Cache management UI components
    private lateinit var cacheStatsText: TextView
    private lateinit var cacheListContainer: LinearLayout
    private lateinit var searchInput: EditText
    private var currentSearchKeyword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = LyricsDatabaseHelper.getInstance(this)

        val prefs = getSharedPreferences(CarBluetoothLyricConfig.PREFS_NAME, Context.MODE_PRIVATE)
        var currentConfig = CarBluetoothLyricConfig.fromPreferences(prefs)

        fun saveConfig(newConfig: CarBluetoothLyricConfig) {
            currentConfig = newConfig
            prefs.edit()
                .putBoolean(CarBluetoothLyricConfig.KEY_ENABLED, newConfig.enabled)
                .putBoolean(CarBluetoothLyricConfig.KEY_ONLY_BLUETOOTH, newConfig.onlyWhenBluetooth)
                .putString(CarBluetoothLyricConfig.KEY_DISPLAY_MODE, newConfig.displayMode.key)
                .putLong(CarBluetoothLyricConfig.KEY_OFFSET_MS, newConfig.offsetMs)
                .apply()
        }

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(32), dp(18), dp(40))
        }
        root.addView(container)

        // Header Title
        val titleText = TextView(this).apply {
            text = "YouTube Music HyperLyric"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(titleText)

        val subTitleText = TextView(this).apply {
            text = "HyperLyric 状态栏/超级岛 & 车载蓝牙歌词增强模块"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 13f
            setPadding(0, dp(4), 0, dp(18))
        }
        container.addView(subTitleText)

        // 1. Car Bluetooth Settings Card
        val carCard = createCard().apply {
            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = "🚗 车载蓝牙歌词设置"
                setTextColor(Color.parseColor("#64B5F6"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            }
            addView(sectionTitle)

            // Switch: Enable Car Bluetooth Lyrics
            val enableSwitch = Switch(this@SettingsActivity).apply {
                text = "启用车载蓝牙显示歌词"
                setTextColor(Color.WHITE)
                textSize = 15f
                isChecked = currentConfig.enabled
                setOnCheckedChangeListener { _, isChecked ->
                    saveConfig(currentConfig.copy(enabled = isChecked))
                }
            }
            addView(enableSwitch)

            // Switch: Only When Bluetooth Audio Connected
            val onlyBtSwitch = Switch(this@SettingsActivity).apply {
                text = "仅连接蓝牙音频时生效"
                setTextColor(Color.WHITE)
                textSize = 15f
                isChecked = currentConfig.onlyWhenBluetooth
                setPadding(0, dp(10), 0, 0)
                setOnCheckedChangeListener { _, isChecked ->
                    saveConfig(currentConfig.copy(onlyWhenBluetooth = isChecked))
                }
            }
            addView(onlyBtSwitch)

            val onlyBtHint = TextView(this@SettingsActivity).apply {
                text = "开启后，未连接蓝牙时不替换手机通知栏与锁屏歌名；连接汽车或蓝牙耳机时自动激活歌词推送，暂停或断开时自动恢复原歌名。"
                setTextColor(Color.parseColor("#757575"))
                textSize = 12f
                setPadding(0, dp(4), 0, dp(16))
            }
            addView(onlyBtHint)

            // RadioGroup: Display Mode
            val modeLabel = TextView(this@SettingsActivity).apply {
                text = "歌词展示槽位与样式"
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(6))
            }
            addView(modeLabel)

            val radioGroup = RadioGroup(this@SettingsActivity)
            val modes = listOf(
                LyricDisplayMode.TITLE_ONLY to "标题栏替换 (推荐，车机屏幕字号最大最清晰)",
                LyricDisplayMode.TITLE_WITH_SONG to "标题栏拼接 (歌名 - 歌词)",
                LyricDisplayMode.ARTIST_ONLY to "歌手栏替换 (歌名保留，歌手栏显示歌词)",
                LyricDisplayMode.ALBUM_ONLY to "专辑栏替换 (歌名与歌手保留，专辑栏显示歌词)",
            )

            modes.forEach { (mode, label) ->
                val rb = RadioButton(this@SettingsActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#E0E0E0"))
                    textSize = 13f
                    id = View.generateViewId()
                    isChecked = (mode == currentConfig.displayMode)
                    setPadding(dp(6), dp(4), 0, dp(4))
                }
                radioGroup.addView(rb)
                if (mode == currentConfig.displayMode) {
                    radioGroup.check(rb.id)
                }
            }

            radioGroup.setOnCheckedChangeListener { group, checkedId ->
                val selectedIndex = group.indexOfChild(group.findViewById(checkedId))
                if (selectedIndex in modes.indices) {
                    val chosenMode = modes[selectedIndex].first
                    saveConfig(currentConfig.copy(displayMode = chosenMode))
                }
            }
            addView(radioGroup)

            // Offset adjustment
            val offsetLabel = TextView(this@SettingsActivity).apply {
                text = "时间轴微调补偿 (解决不同车机蓝牙音频延迟)"
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(16), 0, dp(6))
            }
            addView(offsetLabel)

            val offsetLayout = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val offsetValue = TextView(this@SettingsActivity).apply {
                text = "${currentConfig.offsetMs} ms"
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, dp(16), 0)
            }

            fun updateOffset(delta: Long) {
                val newOffset = (currentConfig.offsetMs + delta).coerceIn(-5000L, 5000L)
                saveConfig(currentConfig.copy(offsetMs = newOffset))
                offsetValue.text = "$newOffset ms"
            }

            val minusBtn = Button(this@SettingsActivity).apply {
                text = "-200ms"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#37474F"))
                setOnClickListener { updateOffset(-200L) }
            }
            val plusBtn = Button(this@SettingsActivity).apply {
                text = "+200ms"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#37474F"))
                setOnClickListener { updateOffset(200L) }
            }
            val resetBtn = Button(this@SettingsActivity).apply {
                text = "重置"
                setTextColor(Color.parseColor("#90CAF9"))
                setBackgroundColor(Color.parseColor("#263238"))
                setOnClickListener {
                    saveConfig(currentConfig.copy(offsetMs = 0L))
                    offsetValue.text = "0 ms"
                }
            }

            offsetLayout.addView(offsetValue)
            offsetLayout.addView(minusBtn)
            offsetLayout.addView(plusBtn)
            offsetLayout.addView(resetBtn)
            addView(offsetLayout)
        }
        container.addView(carCard)
        addDivider(container)

        // 2. Local Lyric Cache Management Card
        val cacheCard = createCard().apply {
            val headerRow = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = "📂 本地歌词缓存管理"
                setTextColor(Color.parseColor("#81C784"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(sectionTitle)

            val clearAllBtn = Button(this@SettingsActivity).apply {
                text = "清空缓存"
                setTextColor(Color.parseColor("#EF9A9A"))
                setBackgroundColor(Color.parseColor("#2C1B1B"))
                textSize = 12f
                setOnClickListener { handleClearAllCache() }
            }
            headerRow.addView(clearAllBtn)
            addView(headerRow)

            val cacheDesc = TextView(this@SettingsActivity).apply {
                text = "成功匹配的歌词会自动保存在本地，再次播放直接从本地读取不再消耗流量。点击项目可查看/编辑歌词或重新抓取。"
                setTextColor(Color.parseColor("#9E9E9E"))
                textSize = 12f
                setPadding(0, dp(6), 0, dp(10))
            }
            addView(cacheDesc)

            cacheStatsText = TextView(this@SettingsActivity).apply {
                text = "正在统计缓存数据..."
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(8))
            }
            addView(cacheStatsText)

            // Search Filter
            searchInput = EditText(this@SettingsActivity).apply {
                hint = "🔍 搜索歌名或歌手过滤列表..."
                setHintTextColor(Color.parseColor("#546E7A"))
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#181818"))
                textSize = 13f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(10)
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        currentSearchKeyword = s?.toString()?.trim().orEmpty()
                        loadCacheList()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            addView(searchInput)

            // Dynamic Cache Items Container
            cacheListContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            addView(cacheListContainer)
        }
        container.addView(cacheCard)
        addDivider(container)

        // 3. Test Lyric Search Card
        val testCard = createCard().apply {
            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = "🔍 歌词三源检索即时测试"
                setTextColor(Color.parseColor("#FFB74D"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(8))
            }
            addView(sectionTitle)

            val titleEdit = EditText(this@SettingsActivity).apply {
                hint = "歌曲标题 (如: 晴天)"
                setHintTextColor(Color.parseColor("#757575"))
                setTextColor(Color.WHITE)
                setText("晴天")
                textSize = 14f
            }
            addView(titleEdit)

            val artistEdit = EditText(this@SettingsActivity).apply {
                hint = "艺术家/歌手 (如: 周杰伦)"
                setHintTextColor(Color.parseColor("#757575"))
                setTextColor(Color.WHITE)
                setText("周杰伦")
                textSize = 14f
            }
            addView(artistEdit)

            val resultText = TextView(this@SettingsActivity).apply {
                text = "点击下方按钮测试 LRCLIB + 网易云 + 酷狗三源抓取并预览车机格式化效果..."
                setTextColor(Color.parseColor("#9E9E9E"))
                textSize = 12f
                setPadding(0, dp(8), 0, dp(8))
                setLineSpacing(dp(2).toFloat(), 1.0f)
            }
            addView(resultText)

            val testBtn = Button(this@SettingsActivity).apply {
                text = "开始测试检索"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1565C0"))
                setOnClickListener {
                    val t = titleEdit.text.toString().trim()
                    val a = artistEdit.text.toString().trim()
                    if (t.isBlank()) {
                        Toast.makeText(this@SettingsActivity, "请输入歌曲标题", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    resultText.text = "正在联网检索中，请稍候..."
                    isEnabled = false

                    bgExecutor.execute {
                        val lines = LyricsRepository.getLyrics(t, a, "", 240_000L, this@SettingsActivity)
                        mainHandler.post {
                            isEnabled = true
                            if (lines.isNullOrEmpty()) {
                                resultText.text = "❌ 未检索到同步歌词，请检查歌名/歌手输入或网络。"
                            } else {
                                val first3 = lines.take(3).joinToString("\n") { "[${it.begin}ms] ${it.text}" }
                                val sampleLine = lines.getOrNull(1) ?: lines.first()
                                val (formattedTitle, formattedArtist, formattedAlbum) = CarLyricTicker.formatMetadata(
                                    origTitle = t,
                                    origArtist = a,
                                    origAlbum = "测试专辑",
                                    activeLine = sampleLine,
                                    mode = currentConfig.displayMode,
                                    )
                                resultText.text = "✅ 成功匹配 ${lines.size} 行同步歌词！(已自动保存至本地缓存)\n\n【车机推送预览 (${currentConfig.displayMode.displayName})】\n• Title: $formattedTitle\n• Artist: $formattedArtist\n• Album: $formattedAlbum\n\n【歌词前3句示例】\n$first3"
                                loadCacheList()
                            }
                        }
                    }
                }
            }
            addView(testBtn)
        }
        container.addView(testCard)
        addDivider(container)

        // 4. Status Card (Scope Guide)
        val statusCard = createCard().apply {
            val scopeTitle = TextView(this@SettingsActivity).apply {
                text = "📌 LSPosed 作用域配置指引"
                setTextColor(Color.parseColor("#81C784"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(scopeTitle)

            val scopeDesc = TextView(this@SettingsActivity).apply {
                text = "• 系统界面 (System UI)：用于 HyperLyric 状态栏/灵动岛胶囊歌词\n• YouTube Music：用于车载蓝牙 (AVRCP) 屏幕显示歌词\n两者可同时勾选，独立运行且互不冲突。"
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 13f
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(4).toFloat(), 1.0f)
            }
            addView(scopeDesc)
        }
        container.addView(statusCard)

        setContentView(root)
        loadCacheList()
    }

    override fun onResume() {
        super.onResume()
        loadCacheList()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            loadCacheList()
        }
    }

    private fun loadCacheList() {
        bgExecutor.execute {
            val kw = currentSearchKeyword.takeIf { it.isNotBlank() }
            val count = dbHelper.getCount(kw)
            val list = dbHelper.getAll(searchKeyword = kw, limit = 100)

            mainHandler.post {
                updateCacheUi(count, list)
            }
        }
    }

    private fun updateCacheUi(count: Int, list: List<LyricsCacheEntry>) {
        if (currentSearchKeyword.isBlank()) {
            cacheStatsText.text = "共已缓存 $count 首歌曲"
        } else {
            cacheStatsText.text = "搜索结果: $count 首歌曲 (筛选: \"$currentSearchKeyword\")"
        }

        cacheListContainer.removeAllViews()

        if (list.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = if (currentSearchKeyword.isBlank()) {
                    "暂无本地缓存歌词。在 YouTube Music 中播放歌曲后将自动缓存。"
                } else {
                    "未搜索到匹配的本地缓存歌词"
                }
                setTextColor(Color.parseColor("#757575"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
            cacheListContainer.addView(emptyView)
            return
        }

        list.forEach { entry ->
            val itemCard = createCacheItemView(entry)
            cacheListContainer.addView(itemCard)
        }
    }

    private fun createCacheItemView(entry: LyricsCacheEntry): View {
        val itemRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                LyricDetailActivity.start(this@SettingsActivity, entry.cacheKey, 1001)
            }
        }

        // Info Column
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val songTitle = TextView(this).apply {
            text = entry.title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        infoCol.addView(songTitle)

        val songArtist = TextView(this).apply {
            text = entry.artist
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            maxLines = 1
            setPadding(0, dp(2), 0, dp(4))
        }
        infoCol.addView(songArtist)

        // Metadata tag row
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sourceBadge = TextView(this).apply {
            text = entry.source
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(1), dp(6), dp(1))
            setTextColor(Color.WHITE)
            when (entry.source) {
                "LRCLIB" -> setBackgroundColor(Color.parseColor("#1565C0"))
                "网易云" -> setBackgroundColor(Color.parseColor("#C62828"))
                "酷狗" -> setBackgroundColor(Color.parseColor("#00838F"))
                "自定义编辑" -> setBackgroundColor(Color.parseColor("#E65100"))
                else -> setBackgroundColor(Color.parseColor("#455A64"))
            }
        }
        tagRow.addView(sourceBadge)

        val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.updatedAt))
        val dateText = TextView(this).apply {
            text = "  •  $dateStr"
            setTextColor(Color.parseColor("#616161"))
            textSize = 11f
        }
        tagRow.addView(dateText)
        infoCol.addView(tagRow)

        itemRoot.addView(infoCol)

        // Quick Delete Button
        val deleteBtn = Button(this).apply {
            text = "删除"
            setTextColor(Color.parseColor("#EF9A9A"))
            setBackgroundColor(Color.parseColor("#2B2121"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36),
            ).apply {
                marginStart = dp(8)
            }
            setOnClickListener {
                confirmDeleteSingle(entry)
            }
        }
        itemRoot.addView(deleteBtn)

        return itemRoot
    }

    private fun confirmDeleteSingle(entry: LyricsCacheEntry) {
        AlertDialog.Builder(this)
            .setTitle("删除缓存")
            .setMessage("确定删除《${entry.title} - ${entry.artist}》的本地歌词缓存吗？")
            .setPositiveButton("删除") { _, _ ->
                dbHelper.delete(entry.cacheKey)
                LyricsRepository.evictFromMemory(entry.cacheKey)
                Toast.makeText(this, "已删除该歌曲缓存", Toast.LENGTH_SHORT).show()
                loadCacheList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleClearAllCache() {
        AlertDialog.Builder(this)
            .setTitle("清空全部缓存")
            .setMessage("确定要清空本地全部歌词缓存吗？清空后所有歌曲将需要重新联网检索。")
            .setPositiveButton("全部清空") { _, _ ->
                val deleted = dbHelper.deleteAll()
                LyricsRepository.clearMemoryCache()
                Toast.makeText(this, "已清空 $deleted 条本地歌词缓存", Toast.LENGTH_SHORT).show()
                loadCacheList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun addDivider(parent: LinearLayout) {
        val view = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(16),
            )
        }
        parent.addView(view)
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
