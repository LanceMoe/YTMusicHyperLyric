package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import moe.lance.ytmusiclyric.LrcTimeShift
import moe.lance.ytmusiclyric.LyricsRepository
import moe.lance.ytmusiclyric.R
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import moe.lance.ytmusiclyric.cache.LyricsDatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LyricDetailActivity : Activity() {

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var ui: HyperStyle
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
        // System theme changes recreate the activity; keep unsaved edits and their source.
        if (savedInstanceState != null) {
            searchTitleInput.setText(savedInstanceState.getString("searchTitle", currentEntry?.title.orEmpty()))
            searchArtistInput.setText(savedInstanceState.getString("searchArtist", currentEntry?.artist.orEmpty()))
            suppressShiftWatcher = true
            lrcEditText.setText(savedInstanceState.getString("draft", currentEntry?.rawLrc.orEmpty()))
            suppressShiftWatcher = false
            lrcEditText.setSelection(savedInstanceState.getInt("selection", 0).coerceIn(0, lrcEditText.length()))
            shiftBaseLrc = savedInstanceState.getString("shiftBase")
            appliedShiftMs = savedInstanceState.getLong("shiftMs")
            shiftValueView.text = if (appliedShiftMs == 0L) "本次未位移"
            else "本次位移：${if (appliedShiftMs >= 0) "+" else ""}$appliedShiftMs ms"
            val pendingLrc = savedInstanceState.getString("pendingLrc")
            val pendingSource = savedInstanceState.getString("pendingSource")
            if (pendingLrc != null && pendingSource != null) pendingSearchResult = pendingLrc to pendingSource
            searchStatus.text = savedInstanceState.getString("searchStatus", "")
        }
    }

    private fun setupUi() {
        ui = HyperStyle(this)
        val content = ui.page("歌词详情", "搜索、编辑与校准同步歌词", back = true)
        val meta = ui.paddedContent(ui.section(content, "歌曲信息"))
        titleView = ui.label("", 22f, bold = true)
        artistView = ui.label("", 15f, ui.secondary).apply { setPadding(0, ui.dp(4), 0, ui.dp(12)) }
        sourceBadge = ui.badge("")
        timeView = ui.label("", 12f, ui.secondary).apply { setPadding(0, ui.dp(10), 0, 0) }
        meta.addView(titleView)
        meta.addView(artistView)
        meta.addView(sourceBadge, LinearLayout.LayoutParams(-2, -2))
        meta.addView(timeView)

        val search = ui.paddedContent(ui.section(content, "手动搜索"))
        search.addView(ui.label("查找匹配的歌词", 18f, bold = true))
        ui.hint(search, "调整歌名或歌手，搜索结果会填入编辑框。点击「保存修改」后关联到当前歌曲。")
        search.addView(ui.label("歌名或关键词", 13f, ui.secondary).apply { setPadding(0, 0, 0, ui.dp(6)) })
        searchTitleInput = ui.input("歌名或搜索关键词", currentEntry?.title.orEmpty())
        search.addView(searchTitleInput)
        search.addView(ui.label("歌手", 13f, ui.secondary).apply { setPadding(0, 0, 0, ui.dp(6)) })
        searchArtistInput = ui.input("歌手（可留空）", currentEntry?.artist.orEmpty())
        search.addView(searchArtistInput)
        searchBtn = ui.button("搜索歌词") { handleManualSearch() }
        search.addView(searchBtn, LinearLayout.LayoutParams(-1, -2))
        searchStatus = ui.hint(search, "").apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

        val timeline = ui.paddedContent(ui.section(content, "时间轴校准"))
        timeline.addView(ui.label("整体提前或延后", 18f, bold = true))
        ui.hint(timeline, "调整整首歌词的时间，负数提前、正数延后。点击「保存修改」后生效。")
        shiftValueView = ui.label("本次未位移", 22f, ui.primary, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, ui.dp(14))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        timeline.addView(shiftValueView, LinearLayout.LayoutParams(-1, -2))
        val quickShift = ui.row()
        listOf("−5 秒" to -5000L, "−1 秒" to -1000L, "+1 秒" to 1000L, "+5 秒" to 5000L).forEachIndexed { index, (label, delta) ->
            quickShift.addView(ui.button(label) { applyTimelineShift(delta) }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index < 3) marginEnd = ui.dp(6)
            })
        }
        timeline.addView(quickShift)
        timeline.addView(ui.label("自定义位移（毫秒）", 13f, ui.secondary).apply {
            setPadding(0, ui.dp(16), 0, ui.dp(8))
        })
        val customShiftInput = ui.input("例如 350 或 -800").apply {
            id = R.id.custom_shift
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        timeline.addView(customShiftInput)
        val shiftActions = ui.row()
        shiftActions.addView(ui.button("应用位移") {
            val delta = customShiftInput.text.toString().trim().toLongOrNull()
            if (delta == null) customShiftInput.error = "请输入有效的毫秒数"
            else {
                applyTimelineShift(delta)
                customShiftInput.text.clear()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
        shiftActions.addView(ui.button("撤销位移") { resetTimelineShift() }, LinearLayout.LayoutParams(0, -2, 1f))
        timeline.addView(shiftActions)

        val editor = ui.paddedContent(ui.section(content, "歌词编辑"))
        editor.addView(ui.label("LRC 文本", 18f, bold = true))
        ui.hint(editor, "可直接粘贴或编辑带时间戳的歌词，保存后同步至本地缓存。")
        lrcEditText = ui.input("暂无歌词，可先搜索或在此粘贴 LRC 歌词", multiline = true).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        editor.addView(lrcEditText)
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
        saveBtn = ui.button("保存修改", prominent = true) { handleSave() }
        editor.addView(saveBtn, LinearLayout.LayoutParams(-1, -2))
        val actions = ui.row()
        redownloadBtn = ui.button("重新下载") { handleRedownload() }
        deleteBtn = ui.button("删除缓存", destructive = true) { handleDelete() }
        actions.addView(redownloadBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8) })
        actions.addView(deleteBtn, LinearLayout.LayoutParams(0, -2, 1f))
        editor.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
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

    private fun populateData() {
        val entry = currentEntry ?: return
        titleView.text = entry.title
        artistView.text = entry.artist

        sourceBadge.text = entry.displaySource
        sourceBadge.setTextColor(if (entry.hasLyrics) ui.primary else ui.error)
        sourceBadge.background = ui.shape(if (entry.hasLyrics) ui.primaryContainer else ui.errorContainer, 6)
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
            Toast.makeText(this, "歌词保存成功，已同步至本地缓存", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
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
            ui.showDialog(AlertDialog.Builder(this)
                .setTitle("替换未保存的修改？")
                .setMessage("获取成功后将替换编辑框中未保存的歌词，获取失败时保留现有内容。")
                .setPositiveButton("继续") { _, _ -> action() }
                .setNegativeButton("取消", null))
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

    override fun onSaveInstanceState(outState: Bundle) {
        if (::lrcEditText.isInitialized) {
            outState.putString("searchTitle", searchTitleInput.text.toString())
            outState.putString("searchArtist", searchArtistInput.text.toString())
            outState.putString("draft", lrcEditText.text.toString())
            outState.putInt("selection", lrcEditText.selectionStart)
            outState.putString("shiftBase", shiftBaseLrc)
            outState.putLong("shiftMs", appliedShiftMs)
            outState.putString("pendingLrc", pendingSearchResult?.first)
            outState.putString("pendingSource", pendingSearchResult?.second)
            outState.putString("searchStatus", if (isFetching) "检索已中断，可重新搜索。" else searchStatus.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        bgExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleDelete() {
        ui.showDialog(AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这首歌曲的本地歌词缓存吗？删除后下次播放将重新从网络检索。")
            .setPositiveButton("删除") { _, _ ->
                dbHelper.delete(cacheKey)
                LyricsRepository.evictFromMemory(cacheKey)
                setResult(RESULT_OK)
                Toast.makeText(this, "已删除歌词缓存", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("取消", null))
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
