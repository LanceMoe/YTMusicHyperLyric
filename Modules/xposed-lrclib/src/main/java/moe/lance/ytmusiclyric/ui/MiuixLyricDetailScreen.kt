package moe.lance.ytmusiclyric.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal data class LyricDetailUiState(
    val entry: LyricsCacheEntry,
    val searchTitle: String,
    val searchArtist: String,
    val draft: String,
    val pendingSearchResult: Pair<String, String>? = null,
    val searchStatus: String = "",
    val isFetching: Boolean = false,
    val shiftBaseLrc: String? = null,
    val appliedShiftMs: Long = 0L,
    val showReplaceDraftDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
)

@Composable
internal fun MiuixLyricDetailScreen(
    state: LyricDetailUiState,
    onBack: () -> Unit,
    onSearchTitleChanged: (String) -> Unit,
    onSearchArtistChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onShift: (Long) -> Unit,
    onResetShift: () -> Unit,
    onSave: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onDismissReplaceDraft: () -> Unit,
    onConfirmReplaceDraft: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    HyperLyricTheme {
        val scrollState = rememberScrollState()
        var searchTitle by remember(state.entry.cacheKey) { mutableStateOf(TextFieldValue(state.searchTitle)) }
        var searchArtist by remember(state.entry.cacheKey) { mutableStateOf(TextFieldValue(state.searchArtist)) }
        var draft by remember(state.entry.cacheKey) { mutableStateOf(TextFieldValue(state.draft)) }
        var customShift by remember(state.entry.cacheKey) { mutableStateOf(TextFieldValue()) }
        var customShiftError by remember(state.entry.cacheKey) { mutableStateOf(false) }

        LaunchedEffect(state.searchTitle) {
            if (searchTitle.text != state.searchTitle) searchTitle = TextFieldValue(state.searchTitle)
        }
        LaunchedEffect(state.searchArtist) {
            if (searchArtist.text != state.searchArtist) searchArtist = TextFieldValue(state.searchArtist)
        }
        LaunchedEffect(state.draft) {
            if (draft.text != state.draft) draft = TextFieldValue(state.draft)
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                SmallTopAppBar(
                    title = "歌词详情与编辑",
                    subtitle = state.entry.title,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowRight,
                                contentDescription = "返回",
                                modifier = Modifier.graphicsLayer { scaleX = -1f },
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            text = "保存",
                            onClick = onSave,
                            enabled = !state.isFetching,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // 歌曲信息
                SmallTitle(text = "歌曲信息")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(state.entry.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            state.entry.artist.ifBlank { "未知歌手" },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (state.entry.hasLyrics) MiuixTheme.colorScheme.primaryContainer
                                        else MiuixTheme.colorScheme.errorContainer
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = if (state.entry.hasLyrics) "来源：${state.entry.displaySource}" else "下载失败",
                                    color = if (state.entry.hasLyrics) MiuixTheme.colorScheme.onPrimaryContainer
                                    else MiuixTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                detailTime(state.entry),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                // 手动检索
                SmallTitle(text = "手动搜索歌词")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("联网查找匹配的歌词", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "若自动匹配不准，可调整歌名或歌手重新检索三源。结果将填入下方编辑区，保存后生效。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        MiuixInput(
                            value = searchTitle,
                            onValueChange = {
                                searchTitle = it
                                onSearchTitleChanged(it.text)
                            },
                            label = "歌名或关键词",
                            placeholder = "输入歌名或检索关键词",
                        )
                        MiuixInput(
                            value = searchArtist,
                            onValueChange = {
                                searchArtist = it
                                onSearchArtistChanged(it.text)
                            },
                            label = "歌手",
                            placeholder = "歌手名称（可选）",
                        )
                        Button(
                            onClick = onSearch,
                            enabled = !state.isFetching,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(if (state.isFetching) "联网检索中…" else "搜索歌词")
                        }
                        if (state.searchStatus.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                    .padding(10.dp),
                            ) {
                                Text(
                                    state.searchStatus,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // 时间轴校准
                SmallTitle(text = "时间轴校准")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("整体提前或延后", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "微调整首歌词的起止时间轴。负数提前、正数延后。微调后请点击「保存修改」生效。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        val applied = state.appliedShiftMs
                        val shiftColor = when {
                            applied > 0 -> MiuixTheme.colorScheme.primary
                            applied < 0 -> Color(0xFFE67E22)
                            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (applied == 0L) "0 ms（未位移）"
                                    else "${if (applied > 0) "+" else ""}$applied ms",
                                    color = shiftColor,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (applied != 0L) {
                                    Text(
                                        text = if (applied > 0) "整首歌词延后 $applied ms" else "整首歌词提前 ${-applied} ms",
                                        color = shiftColor.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("−5 秒" to -5000L, "−1 秒" to -1000L, "+1 秒" to 1000L, "+5 秒" to 5000L).forEach { (label, delta) ->
                                Button(
                                    onClick = { onShift(delta) },
                                    enabled = !state.isFetching,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(label, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        MiuixInput(
                            value = customShift,
                            onValueChange = {
                                customShift = it
                                customShiftError = false
                            },
                            label = "自定义位移（毫秒）",
                            placeholder = "例如 350 或 -800",
                            keyboardType = KeyboardType.Ascii,
                        )
                        if (customShiftError) {
                            Text(
                                "请输入有效的整数毫秒（如 500 或 -300）",
                                color = MiuixTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val delta = customShift.text.trim().toLongOrNull()
                                    if (delta == null) customShiftError = true else {
                                        onShift(delta)
                                        customShift = TextFieldValue()
                                    }
                                },
                                enabled = !state.isFetching,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("应用位移")
                            }
                            Button(
                                onClick = onResetShift,
                                enabled = !state.isFetching,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("撤销位移")
                            }
                        }
                    }
                }

                // 歌词编辑
                SmallTitle(text = "歌词编辑")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("LRC 文本", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "可直接粘贴或修改带时间戳的歌词内容，保存后同步至本地缓存。",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        MiuixInput(
                            value = draft,
                            onValueChange = {
                                draft = it
                                onDraftChanged(it.text)
                            },
                            label = "LRC 文本",
                            placeholder = "暂无歌词内容，可先搜索或在此粘贴 LRC 格式歌词",
                            multiline = true,
                        )
                        Button(
                            onClick = onSave,
                            enabled = !state.isFetching,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("保存修改")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onRedownload,
                                enabled = !state.isFetching,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("重新下载")
                            }
                            Button(
                                onClick = onDelete,
                                enabled = !state.isFetching,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.errorContainer,
                                    contentColor = MiuixTheme.colorScheme.error,
                                ),
                            ) {
                                Text("删除缓存")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        // 替换草稿确认弹窗
        if (state.showReplaceDraftDialog) {
            WindowDialog(
                show = true,
                onDismissRequest = onDismissReplaceDraft,
                title = "替换未保存的修改？",
                summary = "获取成功后将替换编辑框中未保存的歌词内容，获取失败时将保留现有编辑。",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onDismissReplaceDraft,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onConfirmReplaceDraft,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("继续替换")
                    }
                }
            }
        }

        // 删除缓存确认弹窗
        if (state.showDeleteDialog) {
            WindowDialog(
                show = true,
                onDismissRequest = onDismissDelete,
                title = "确认删除",
                summary = "确定要删除这首歌曲的本地歌词缓存吗？删除后下次播放将重新从网络检索。",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onDismissDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onConfirmDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

private fun detailTime(entry: LyricsCacheEntry): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(entry.updatedAt))
    val duration = if (entry.durationMs > 0) {
        val totalSec = entry.durationMs / 1000
        " • %02d:%02d".format(totalSec / 60, totalSec % 60)
    } else ""
    return "$date$duration"
}

