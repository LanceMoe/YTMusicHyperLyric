package moe.lance.ytmusiclrc.ui

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
import moe.lance.ytmusiclrc.LrcTimeShift
import moe.lance.ytmusiclrc.LyricsRepository
import moe.lance.ytmusiclrc.cache.LyricsDatabaseHelper
import java.util.concurrent.Executors

class LyricDetailActivity : ComponentActivity() {
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var dbHelper: LyricsDatabaseHelper
    private var cacheKey: String = ""
    private var uiState by mutableStateOf<LyricDetailUiState?>(null)
    private var pendingDraftAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        dbHelper = LyricsDatabaseHelper.getInstance(this)
        cacheKey = intent.getStringExtra(EXTRA_CACHE_KEY).orEmpty()
        if (cacheKey.isBlank()) {
            Toast.makeText(this, "无效的歌词缓存键", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val entry = dbHelper.get(cacheKey)
        if (entry == null) {
            Toast.makeText(this, "未找到该歌曲的缓存记录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        uiState = LyricDetailUiState(
            entry = entry,
            searchTitle = savedInstanceState?.getString(KEY_SEARCH_TITLE) ?: entry.title,
            searchArtist = savedInstanceState?.getString(KEY_SEARCH_ARTIST) ?: entry.artist,
            draft = savedInstanceState?.getString(KEY_DRAFT) ?: entry.rawLrc,
            pendingSearchResult = savedInstanceState?.getString(KEY_PENDING_LRC)?.let { lrc ->
                lrc to (savedInstanceState.getString(KEY_PENDING_SOURCE) ?: "")
            },
            searchStatus = savedInstanceState?.getString(KEY_SEARCH_STATUS)
                ?: if (entry.hasLyrics) "" else "自动下载失败，可修改关键词重新搜索，或直接填写歌词。",
            shiftBaseLrc = savedInstanceState?.getString(KEY_SHIFT_BASE),
            appliedShiftMs = savedInstanceState?.getLong(KEY_SHIFT_MS, 0L) ?: 0L,
        )

        setContent {
            val state = uiState ?: return@setContent
            MiuixLyricDetailScreen(
                state = state,
                onBack = ::finish,
                onSearchTitleChanged = { updateState { copy(searchTitle = it) } },
                onSearchArtistChanged = { updateState { copy(searchArtist = it) } },
                onSearch = ::handleManualSearch,
                onDraftChanged = ::onDraftChanged,
                onShift = ::applyTimelineShift,
                onResetShift = ::resetTimelineShift,
                onSave = ::handleSave,
                onRedownload = ::handleRedownload,
                onDelete = { updateState { copy(showDeleteDialog = true) } },
                onDismissReplaceDraft = { updateState { copy(showReplaceDraftDialog = false) } },
                onConfirmReplaceDraft = {
                    val action = pendingDraftAction
                    updateState { copy(showReplaceDraftDialog = false) }
                    action?.invoke()
                },
                onDismissDelete = { updateState { copy(showDeleteDialog = false) } },
                onConfirmDelete = ::handleConfirmDelete,
            )
        }
    }

    private fun updateState(transform: LyricDetailUiState.() -> LyricDetailUiState) {
        uiState = uiState?.transform()
    }

    private fun onDraftChanged(value: String) {
        updateState {
            if (appliedShiftMs != 0L) {
                copy(draft = value, shiftBaseLrc = null, appliedShiftMs = 0L)
            } else {
                copy(draft = value)
            }
        }
    }

    private fun applyTimelineShift(deltaMs: Long) {
        val state = uiState ?: return
        if (state.isFetching || state.draft.isBlank()) return
        val base = state.shiftBaseLrc ?: state.draft
        val applied = state.appliedShiftMs + deltaMs
        updateState {
            copy(
                draft = LrcTimeShift.apply(base, applied),
                shiftBaseLrc = base,
                appliedShiftMs = applied,
            )
        }
    }

    private fun resetTimelineShift() {
        val state = uiState ?: return
        val base = state.shiftBaseLrc ?: return
        if (state.isFetching) return
        updateState { copy(draft = base, shiftBaseLrc = null, appliedShiftMs = 0L) }
    }

    private fun handleSave() {
        val state = uiState ?: return
        val newLrc = state.draft.trim()
        if (newLrc.isBlank()) {
            Toast.makeText(this, "歌词文本不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val source = state.pendingSearchResult?.takeIf { it.first.trim() == newLrc }?.second ?: "自定义编辑"
        if (!dbHelper.updateLrc(cacheKey, newLrc, source)) {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        LyricsRepository.evictFromMemory(cacheKey)
        val entry = dbHelper.get(cacheKey) ?: return
        uiState = LyricDetailUiState(
            entry = entry,
            searchTitle = entry.title,
            searchArtist = entry.artist,
            draft = entry.rawLrc,
        )
        setResult(RESULT_OK)
        Toast.makeText(this, "歌词保存成功，已同步至本地缓存", Toast.LENGTH_SHORT).show()
    }

    private fun handleRedownload() {
        val state = uiState ?: return
        confirmReplaceDraft { fetchLyrics(state.entry.title, state.entry.artist, state.entry.durationMs, saveImmediately = true) }
    }

    private fun handleManualSearch() {
        val state = uiState ?: return
        val title = state.searchTitle.trim()
        val artist = state.searchArtist.trim()
        if (title.isBlank()) {
            Toast.makeText(this, "请输入歌名或搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }
        confirmReplaceDraft { fetchLyrics(title, artist, durationMs = 0L, saveImmediately = false) }
    }

    private fun confirmReplaceDraft(action: () -> Unit) {
        val state = uiState ?: return
        if (state.isFetching) return
        if (state.draft == state.entry.rawLrc) {
            action()
        } else {
            pendingDraftAction = action
            updateState { copy(showReplaceDraftDialog = true) }
        }
    }

    private fun fetchLyrics(title: String, artist: String, durationMs: Long, saveImmediately: Boolean) {
        updateState { copy(isFetching = true, searchStatus = "正在联网检索歌词…") }
        bgExecutor.execute {
            val result = runCatching {
                LyricsRepository.fetchRawFromProviders(title, artist, durationMs = durationMs)
            }.getOrNull()?.takeIf { it.first.isNotBlank() }
            if (isDestroyed) return@execute
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                if (result == null) {
                    updateState { copy(isFetching = false, searchStatus = "未获取到歌词，请尝试其他关键词或检查网络。") }
                } else if (saveImmediately) {
                    val (newLrc, newSource) = result
                    if (dbHelper.updateLrc(cacheKey, newLrc, newSource)) {
                        LyricsRepository.evictFromMemory(cacheKey)
                        val entry = dbHelper.get(cacheKey) ?: return@post
                        uiState = LyricDetailUiState(
                            entry = entry,
                            searchTitle = entry.title,
                            searchArtist = entry.artist,
                            draft = entry.rawLrc,
                            searchStatus = "重新下载成功，已更新自 $newSource。",
                        )
                        setResult(RESULT_OK)
                    } else {
                        updateState { copy(isFetching = false, searchStatus = "保存失败，请重试。") }
                    }
                } else {
                    updateState {
                        copy(
                            draft = result.first,
                            pendingSearchResult = result,
                            isFetching = false,
                            shiftBaseLrc = null,
                            appliedShiftMs = 0L,
                            searchStatus = "已从 ${result.second} 获取歌词，可在下方编辑；点击“保存修改”后生效。",
                        )
                    }
                }
            }
        }
    }

    private fun handleConfirmDelete() {
        dbHelper.delete(cacheKey)
        LyricsRepository.evictFromMemory(cacheKey)
        setResult(RESULT_OK)
        Toast.makeText(this, "已删除歌词缓存", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        uiState?.let { state ->
            outState.putString(KEY_SEARCH_TITLE, state.searchTitle)
            outState.putString(KEY_SEARCH_ARTIST, state.searchArtist)
            outState.putString(KEY_DRAFT, state.draft)
            outState.putString(KEY_SHIFT_BASE, state.shiftBaseLrc)
            outState.putLong(KEY_SHIFT_MS, state.appliedShiftMs)
            outState.putString(KEY_PENDING_LRC, state.pendingSearchResult?.first)
            outState.putString(KEY_PENDING_SOURCE, state.pendingSearchResult?.second)
            outState.putString(KEY_SEARCH_STATUS, if (state.isFetching) "检索已中断，可重新搜索。" else state.searchStatus)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        bgExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CACHE_KEY = "extra_cache_key"
        private const val KEY_SEARCH_TITLE = "searchTitle"
        private const val KEY_SEARCH_ARTIST = "searchArtist"
        private const val KEY_DRAFT = "draft"
        private const val KEY_SHIFT_BASE = "shiftBase"
        private const val KEY_SHIFT_MS = "shiftMs"
        private const val KEY_PENDING_LRC = "pendingLrc"
        private const val KEY_PENDING_SOURCE = "pendingSource"
        private const val KEY_SEARCH_STATUS = "searchStatus"

        fun start(activity: android.app.Activity, cacheKey: String, requestCode: Int = 1001) {
            val intent = android.content.Intent(activity, LyricDetailActivity::class.java).apply {
                putExtra(EXTRA_CACHE_KEY, cacheKey)
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }
}
