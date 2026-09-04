package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

        setContentView(root)
    }

    private fun populateData() {
        val entry = currentEntry ?: return
        titleView.text = entry.title
        artistView.text = entry.artist

        sourceBadge.text = entry.source
        when (entry.source) {
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

        lrcEditText.setText(entry.rawLrc)
    }

    private fun handleSave() {
        val newLrc = lrcEditText.text.toString().trim()
        if (newLrc.isBlank()) {
            Toast.makeText(this, "歌词文本不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val success = dbHelper.updateLrc(cacheKey, newLrc, "自定义编辑")
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
        redownloadBtn.isEnabled = false
        redownloadBtn.text = "下载中..."

        bgExecutor.execute {
            val result = LyricsRepository.fetchRawFromProviders(
                title = entry.title,
                artist = entry.artist,
                album = "",
                durationMs = entry.durationMs,
            )

            mainHandler.post {
                redownloadBtn.isEnabled = true
                redownloadBtn.text = "🔄 在线重下"

                if (result != null) {
                    val (newLrc, newSource) = result
                    dbHelper.updateLrc(cacheKey, newLrc, newSource)
                    LyricsRepository.evictFromMemory(cacheKey)
                    currentEntry = dbHelper.get(cacheKey)
                    populateData()
                    setResult(RESULT_OK)
                    Toast.makeText(this@LyricDetailActivity, "✅ 重新下载成功，已更新自 $newSource！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@LyricDetailActivity, "❌ 未从网络检索到歌词", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

