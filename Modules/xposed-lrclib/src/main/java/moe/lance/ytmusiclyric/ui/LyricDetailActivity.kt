package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import moe.lance.ytmusiclyric.LrcTimeShift
import moe.lance.ytmusiclyric.LyricsRepository
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import moe.lance.ytmusiclyric.cache.LyricsDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LyricDetailActivity : Activity() {

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var dbHelper: LyricsDatabaseHelper
    private var cacheKey: String = ""
    private var currentEntry: LyricsCacheEntry? = null

    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var sourceBadge: TextView
    private lateinit var timeView: TextView
    private lateinit var lrcEditText: EditText
    private lateinit var saveBtn: Button
    private lateinit var redownloadBtn: Button
    private lateinit var deleteBtn: Button
    private lateinit var searchTitleInput: EditText
    private lateinit var searchArtistInput: EditText
    private lateinit var searchBtn: Button
    private lateinit var searchStatus: TextView
    private var pendingSearchResult: Pair<String, String>? = null
    private var isFetching = false
    private lateinit var shiftValueView: TextView
    private var shiftBaseLrc: String? = null
    private var appliedShiftMs: Long = 0L
    private var suppressShiftWatcher = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = LyricsDatabaseHelper.getInstance(this)

        cacheKey = intent.getStringExtra(EXTRA_CACHE_KEY).orEmpty()
        if (cacheKey.isBlank()) {
            Toast.makeText(this, "无效的歌词缓存键", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentEntry = dbHelper.get(cacheKey)
        if (currentEntry == null) {
            Toast.makeText(this, "未找到该歌曲的缓存记录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUi()
        populateData()
    }

    private fun setupUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        // Top Navigation Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        val backBtn = TextView(this).apply {
            text = "← 返回"
            setTextColor(Color.parseColor("#90CAF9"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(16), 0)
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        val headerTitle = TextView(this).apply {
            text = "歌词详情与编辑"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(headerTitle)
        root.addView(topBar)

        // Scrollable Content
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scrollView.addView(content)
        root.addView(scrollView)

        // Song Metadata Card
        val metaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        metaCard.addView(titleView)

        artistView = TextView(this).apply {
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 14f
            setPadding(0, dp(4), 0, dp(10))
        }
        metaCard.addView(artistView)

        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        sourceBadge = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
        }
        badgeRow.addView(sourceBadge)

        timeView = TextView(this).apply {
            setTextColor(Color.parseColor("#757575"))
            textSize = 12f
            setPadding(dp(12), 0, 0, 0)
        }
        badgeRow.addView(timeView)
        metaCard.addView(badgeRow)
        content.addView(metaCard)

        addSpacer(content, dp(12))

        val searchCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        searchCard.addView(TextView(this).apply {
            text = "手动搜索歌词"
            setTextColor(Color.parseColor("#81C784"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        searchCard.addView(TextView(this).apply {
            text = "可修改歌名关键词和歌手，也可留空歌手。搜索结果填入下方编辑框，点击“保存修改”后关联到当前歌曲。"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        })
        fun searchInput(label: String, value: String): EditText = EditText(this).apply {
            hint = label
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#9E9E9E"))
            textSize = 14f
            setSingleLine(true)
        }
        searchTitleInput = searchInput("歌名或搜索关键词", currentEntry?.title.orEmpty())
        searchArtistInput = searchInput("歌手（可留空）", currentEntry?.artist.orEmpty())
        searchCard.addView(searchTitleInput)
        searchCard.addView(searchArtistInput)
        searchBtn = Button(this).apply {
            text = "搜索歌词"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setOnClickListener { handleManualSearch() }
        }
        searchCard.addView(searchBtn)
        searchStatus = TextView(this).apply {
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
        }
        searchCard.addView(searchStatus)
        content.addView(searchCard)
        addSpacer(content, dp(12))

        // Global timeline shift controls. The shift is applied to every timestamp in the
        // editor and is persisted when the user taps the existing save button.
        val timelineCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val timelineTitle = TextView(this).apply {
            text = "⏱ 歌词时间轴整体位移"
            setTextColor(Color.parseColor("#CE93D8"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        timelineCard.addView(timelineTitle)

        val timelineHint = TextView(this).apply {
            text = "整首歌词统一提前或延后；修改后请点击“保存修改”才会写入缓存。单位：毫秒"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        }
        timelineCard.addView(timelineHint)

        shiftValueView = TextView(this).apply {
            text = "本次未位移"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        timelineCard.addView(shiftValueView)

        val quickShiftRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun addShiftButton(label: String, deltaMs: Long) {
            quickShiftRow.addView(Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#37474F"))
                textSize = 11f
                setOnClickListener { applyTimelineShift(deltaMs) }
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    marginEnd = dp(4)
                }
            })
        }
        addShiftButton("-5秒", -5_000L)
        addShiftButton("-1秒", -1_000L)
        addShiftButton("+1秒", 1_000L)
        addShiftButton("+5秒", 5_000L)
        timelineCard.addView(quickShiftRow)

        val customShiftRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val customShiftInput = EditText(this).apply {
            hint = "例如 350 或 -800"
            setHintTextColor(Color.parseColor("#546E7A"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#181818"))
            textSize = 12f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(6)
                topMargin = dp(6)
            }
        }
        customShiftRow.addView(customShiftInput)
        customShiftRow.addView(Button(this).apply {
            text = "应用毫秒"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6A1B9A"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                topMargin = dp(6)
            }
            setOnClickListener {
                val delta = customShiftInput.text.toString().trim().toLongOrNull()
                if (delta == null) {
                    Toast.makeText(this@LyricDetailActivity, "请输入有效的毫秒数", Toast.LENGTH_SHORT).show()
                } else {
                    applyTimelineShift(delta)
                    customShiftInput.text.clear()
                }
            }
        })
        timelineCard.addView(customShiftRow)

        timelineCard.addView(Button(this).apply {
            text = "撤销本次位移"
            setTextColor(Color.parseColor("#CE93D8"))
            setBackgroundColor(Color.parseColor("#2B1B30"))
            textSize = 11f
            setOnClickListener { resetTimelineShift() }
        })
        content.addView(timelineCard)

        addSpacer(content, dp(12))

        // Action Buttons Row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        saveBtn = Button(this).apply {
            text = "💾 保存修改"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1565C0"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { handleSave() }
        }
        buttonRow.addView(saveBtn)

        redownloadBtn = Button(this).apply {
            text = "🔄 在线重下"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { handleRedownload() }
        }
        buttonRow.addView(redownloadBtn)

        deleteBtn = Button(this).apply {
            text = "🗑️ 删除"
            setTextColor(Color.parseColor("#EF9A9A"))
            setBackgroundColor(Color.parseColor("#372020"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            setOnClickListener { handleDelete() }
        }
        buttonRow.addView(deleteBtn)
        content.addView(buttonRow)

        addSpacer(content, dp(12))

        // Editor Card
        val editorLabel = TextView(this).apply {
            text = "📝 原始 LRC 歌词文本 (支持编辑后保存)"
            setTextColor(Color.parseColor("#FFB74D"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        content.addView(editorLabel)

        lrcEditText = EditText(this).apply {
            hint = "暂无歌词，可先搜索，或在此粘贴、编辑 LRC 歌词后保存"
            setTextColor(Color.parseColor("#ECEFF1"))
            setHintTextColor(Color.parseColor("#546E7A"))
            setBackgroundColor(Color.parseColor("#181818"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.TOP or Gravity.START
            minLines = 15
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        content.addView(lrcEditText)

        lrcEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressShiftWatcher && appliedShiftMs != 0L) {
                    shiftBaseLrc = null
                    appliedShiftMs = 0L
                    shiftValueView.text = "本次未位移"
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        applyWindowInsets(root, topBar, content)

        setContentView(root)
        root.requestApplyInsets()
    }

    private fun applyTimelineShift(deltaMs: Long) {
        if (isFetching) return
        if (lrcEditText.text.toString().isBlank()) return
        if (shiftBaseLrc == null) shiftBaseLrc = lrcEditText.text.toString()
        appliedShiftMs += deltaMs
        val shifted = LrcTimeShift.apply(shiftBaseLrc.orEmpty(), appliedShiftMs)
        suppressShiftWatcher = true
        lrcEditText.setText(shifted)
        lrcEditText.setSelection(lrcEditText.length())
        suppressShiftWatcher = false
        shiftValueView.text = "本次位移：${if (appliedShiftMs >= 0) "+" else ""}$appliedShiftMs ms"
    }

    private fun resetTimelineShift() {
        if (isFetching) return
        val base = shiftBaseLrc ?: return
        suppressShiftWatcher = true
        lrcEditText.setText(base)
        lrcEditText.setSelection(lrcEditText.length())
        suppressShiftWatcher = false
        shiftBaseLrc = null
        appliedShiftMs = 0L
        shiftValueView.text = "本次未位移"
    }

    private fun applyWindowInsets(root: View, topBar: View, content: View) {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topBar.setPadding(dp(16), dp(16) + bars.top, dp(16), dp(12))
            content.setPadding(dp(16), dp(16), dp(16), dp(24) + bars.bottom)
            insets
        }
    }

    private fun populateData() {
        val entry = currentEntry ?: return
        titleView.text = entry.title
        artistView.text = entry.artist

        sourceBadge.text = entry.displaySource
        when (entry.displaySource) {
            "下载失败" -> sourceBadge.setBackgroundColor(Color.parseColor("#8D6E63"))
            "LRCLIB" -> sourceBadge.setBackgroundColor(Color.parseColor("#1565C0"))
            "网易云" -> sourceBadge.setBackgroundColor(Color.parseColor("#C62828"))
            "酷狗" -> sourceBadge.setBackgroundColor(Color.parseColor("#00838F"))
            "自定义编辑" -> sourceBadge.setBackgroundColor(Color.parseColor("#E65100"))
            else -> sourceBadge.setBackgroundColor(Color.parseColor("#455A64"))
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.updatedAt))
        val durationStr = if (entry.durationMs > 0) {
            val totalSec = entry.durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            String.format(Locale.getDefault(), " • 时长 %02d:%02d", min, sec)
        } else ""
        timeView.text = "更新于 $dateStr$durationStr"

        suppressShiftWatcher = true
        lrcEditText.setText(entry.rawLrc)
        suppressShiftWatcher = false
        shiftBaseLrc = null
        appliedShiftMs = 0L
        if (::shiftValueView.isInitialized) shiftValueView.text = "本次未位移"
        pendingSearchResult = null
        searchStatus.text = if (entry.hasLyrics) "" else "自动下载失败，可修改关键词重新搜索，或直接填写歌词。"
    }

    private fun handleSave() {
        val newLrc = lrcEditText.text.toString().trim()
        if (newLrc.isBlank()) {
            Toast.makeText(this, "歌词文本不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val source = pendingSearchResult?.takeIf { it.first.trim() == newLrc }?.second ?: "自定义编辑"
        val success = dbHelper.updateLrc(cacheKey, newLrc, source)
        if (success) {
            LyricsRepository.evictFromMemory(cacheKey)
            currentEntry = dbHelper.get(cacheKey)
            populateData()
            setResult(RESULT_OK)
            Toast.makeText(this, "✅ 歌词保存成功，已同步至本地缓存！", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ 保存失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRedownload() {
        val entry = currentEntry ?: return
        confirmReplaceDraft {
            fetchLyrics(entry.title, entry.artist, entry.durationMs, saveImmediately = true)
        }
    }

    private fun handleManualSearch() {
        val title = searchTitleInput.text.toString().trim()
        val artist = searchArtistInput.text.toString().trim()
        if (title.isBlank()) {
            searchTitleInput.error = "请输入歌名或搜索关键词"
            return
        }
        confirmReplaceDraft {
            // Manual searches should also find versions with a different duration.
            fetchLyrics(title, artist, durationMs = 0L, saveImmediately = false)
        }
    }

    private fun confirmReplaceDraft(action: () -> Unit) {
        if (isFetching) return
        if (lrcEditText.text.toString() == currentEntry?.rawLrc) {
            action()
        } else {
            AlertDialog.Builder(this)
                .setTitle("替换未保存的修改？")
                .setMessage("获取成功后将替换编辑框中未保存的歌词，获取失败时保留现有内容。")
                .setPositiveButton("继续") { _, _ -> action() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun fetchLyrics(title: String, artist: String, durationMs: Long, saveImmediately: Boolean) {
        setFetching(true)
        searchStatus.text = "正在联网检索歌词…"
        bgExecutor.execute {
            val result = runCatching {
                LyricsRepository.fetchRawFromProviders(title, artist, durationMs = durationMs)
            }.getOrNull()?.takeIf { it.first.isNotBlank() }
            if (isDestroyed) return@execute
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                setFetching(false)
                if (result == null) {
                    searchStatus.text = "未获取到歌词，请尝试其他关键词或检查网络。"
                } else if (saveImmediately) {
                    val (newLrc, newSource) = result
                    if (dbHelper.updateLrc(cacheKey, newLrc, newSource)) {
                        LyricsRepository.evictFromMemory(cacheKey)
                        currentEntry = dbHelper.get(cacheKey)
                        populateData()
                        setResult(RESULT_OK)
                        searchStatus.text = "重新下载成功，已更新自 $newSource。"
                    } else {
                        searchStatus.text = "保存失败，请重试。"
                    }
                } else {
                    lrcEditText.setText(result.first)
                    shiftBaseLrc = null
                    appliedShiftMs = 0L
                    shiftValueView.text = "本次未位移"
                    pendingSearchResult = result
                    searchStatus.text = "已从 ${result.second} 获取歌词，可在下方编辑；点击“保存修改”后生效。"
                }
            }
        }
    }

    private fun setFetching(fetching: Boolean) {
        isFetching = fetching
        listOf(searchBtn, redownloadBtn, saveBtn, deleteBtn, searchTitleInput, searchArtistInput, lrcEditText)
            .forEach { it.isEnabled = !fetching }
        searchBtn.text = if (fetching) "检索中…" else "搜索歌词"
    }

    override fun onDestroy() {
        bgExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleDelete() {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这首歌曲的本地歌词缓存吗？删除后下次播放将重新从网络检索。")
            .setPositiveButton("删除") { _, _ ->
                dbHelper.delete(cacheKey)
                LyricsRepository.evictFromMemory(cacheKey)
                setResult(RESULT_OK)
                Toast.makeText(this, "已删除歌词缓存", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSpacer(parent: LinearLayout, height: Int) {
        val view = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
        parent.addView(view)
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_CACHE_KEY = "extra_cache_key"

        fun start(activity: Activity, cacheKey: String, requestCode: Int = 1001) {
            val intent = Intent(activity, LyricDetailActivity::class.java).apply {
                putExtra(EXTRA_CACHE_KEY, cacheKey)
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }
}
