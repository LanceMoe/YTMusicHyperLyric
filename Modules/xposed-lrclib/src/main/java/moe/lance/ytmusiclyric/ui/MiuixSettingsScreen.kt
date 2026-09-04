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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.lance.ytmusiclyric.CarBluetoothLyricConfig
import moe.lance.ytmusiclyric.LyricDisplayMode
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal data class SettingsUiState(
    val config: CarBluetoothLyricConfig,
    val selectedTab: Int = 0,
    val cacheEntries: List<LyricsCacheEntry> = emptyList(),
    val cacheCount: Int = 0,
    val cacheSearch: String = "",
    val testResult: String = "输入歌曲信息，开始检索同步歌词。",
    val testLoading: Boolean = false,
    val testError: Boolean = false,
    val restartStatus: String = "",
    val restartLoading: Boolean = false,
    val launcherIconHidden: Boolean = false,
    val showModeDialog: Boolean = false,
    val pendingDeleteEntry: LyricsCacheEntry? = null,
    val showClearAllDialog: Boolean = false,
)

@Composable
internal fun MiuixSettingsScreen(
    state: SettingsUiState,
    onTabSelected: (Int) -> Unit,
    onConfigChanged: (CarBluetoothLyricConfig) -> Unit,
    onModeClick: () -> Unit,
    onDismissModeDialog: () -> Unit,
    onSelectMode: (LyricDisplayMode) -> Unit,
    onLauncherIconChanged: (Boolean) -> Unit,
    onRestartSystemUi: () -> Unit,
    onCacheSearchChanged: (String) -> Unit,
    onClearCache: () -> Unit,
    onDismissClearAll: () -> Unit,
    onConfirmClearAll: () -> Unit,
    onCacheClick: (LyricsCacheEntry) -> Unit,
    onDeleteCache: (LyricsCacheEntry) -> Unit,
    onDismissDeleteSingle: () -> Unit,
    onConfirmDeleteSingle: (LyricsCacheEntry) -> Unit,
    onTestSearch: (String, String) -> Unit,
) {
    HyperLyricTheme {
        val scrollBehavior = MiuixScrollBehavior()
        var title by remember { mutableStateOf(TextFieldValue()) }
        var artist by remember { mutableStateOf(TextFieldValue()) }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = if (state.selectedTab == 0) "设置" else "歌词缓存",
                    largeTitle = if (state.selectedTab == 0) "设置" else "歌词缓存",
                    subtitle = "YouTube Music HyperLyric",
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                TabRow(
                    tabs = listOf("功能设置", "本地缓存"),
                    selectedTabIndex = state.selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )

                if (state.selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        SettingsContent(
                            state = state,
                            title = title,
                            artist = artist,
                            onTitleChanged = { title = it },
                            onArtistChanged = { artist = it },
                            onConfigChanged = onConfigChanged,
                            onModeClick = onModeClick,
                            onLauncherIconChanged = onLauncherIconChanged,
                            onRestartSystemUi = onRestartSystemUi,
                            onTestSearch = { onTestSearch(title.text.trim(), artist.text.trim()) },
                        )

                        Text(
                            text = "YouTube Music HyperLyric · 歌词增强模块",
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp, bottom = 24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        CacheContent(
                            state = state,
                            onCacheSearchChanged = onCacheSearchChanged,
                            onClearCache = onClearCache,
                            onCacheClick = onCacheClick,
                            onDeleteCache = onDeleteCache,
                        )

                        Text(
                            text = "YouTube Music HyperLyric · 歌词增强模块",
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp, bottom = 24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // 歌词显示位置选择弹窗
        if (state.showModeDialog) {
            WindowDialog(
                show = true,
                onDismissRequest = onDismissModeDialog,
                title = "歌词显示位置",
                summary = "选择推送到车载中控屏的对应栏目",
            ) {
                val modes = listOf(
                    LyricDisplayMode.TITLE_ONLY to ("标题栏替换（推荐）" to "将同步歌词推送到车机的歌曲标题栏"),
                    LyricDisplayMode.TITLE_WITH_SONG to ("标题栏拼接" to "循环滚动展示：歌名 - 歌词"),
                    LyricDisplayMode.ARTIST_ONLY to ("歌手栏替换" to "歌词显示在歌手栏，保留原歌曲标题"),
                    LyricDisplayMode.ALBUM_ONLY to ("专辑栏替换" to "歌词显示在专辑栏，保留歌名与歌手"),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    modes.forEachIndexed { index, (mode, pair) ->
                        val (modeTitle, modeSummary) = pair
                        RadioButtonPreference(
                            title = modeTitle,
                            summary = modeSummary,
                            selected = (state.config.displayMode == mode),
                            onClick = { onSelectMode(mode) },
                        )
                        if (index < modes.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDismissModeDialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("取消")
                }
            }
        }

        // 删除单项缓存确认弹窗
        if (state.pendingDeleteEntry != null) {
            val entry = state.pendingDeleteEntry
            WindowDialog(
                show = true,
                onDismissRequest = onDismissDeleteSingle,
                title = "删除缓存",
                summary = "确定删除《${entry.title} - ${entry.artist.ifBlank { "未知歌手" }}》的本地歌词缓存吗？删除后下次播放将重新联网检索。",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onDismissDeleteSingle,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = { onConfirmDeleteSingle(entry) },
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

        // 清空全部缓存确认弹窗
        if (state.showClearAllDialog) {
            WindowDialog(
                show = true,
                onDismissRequest = onDismissClearAll,
                title = "清空全部缓存",
                summary = "确定清空本地全部 ${state.cacheCount} 条歌词缓存吗？所有已保存的歌词与时间轴校准将被清除，下次播放需要重新检索。",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onDismissClearAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onConfirmClearAll,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                    ) {
                        Text("全部清空")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    title: TextFieldValue,
    artist: TextFieldValue,
    onTitleChanged: (TextFieldValue) -> Unit,
    onArtistChanged: (TextFieldValue) -> Unit,
    onConfigChanged: (CarBluetoothLyricConfig) -> Unit,
    onModeClick: () -> Unit,
    onLauncherIconChanged: (Boolean) -> Unit,
    onRestartSystemUi: () -> Unit,
    onTestSearch: () -> Unit,
) {
    Section(title = "车载蓝牙歌词") {
        SwitchPreference(
            checked = state.config.enabled,
            onCheckedChange = { onConfigChanged(state.config.copy(enabled = it)) },
            title = "显示车载歌词",
            summary = "将同步歌词推送到车机的歌曲信息栏",
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        SwitchPreference(
            checked = state.config.onlyWhenBluetooth,
            onCheckedChange = { onConfigChanged(state.config.copy(onlyWhenBluetooth = it)) },
            title = "仅在连接蓝牙时生效",
            summary = "暂停或断开蓝牙后恢复原歌名，避免影响手机通知栏与锁屏",
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        ArrowPreference(
            title = "歌词显示位置",
            summary = "${state.config.displayMode.displayName} · 点击更换",
            onClick = onModeClick,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(text = "蓝牙延迟补偿", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(
                text = "根据车机音频传输延迟微调，负数提前、正数延后，范围 ±5000 ms",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val offset = state.config.offsetMs
            val offsetColor = when {
                offset > 0 -> MiuixTheme.colorScheme.primary
                offset < 0 -> Color(0xFFE67E22)
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
            val offsetFormatted = when {
                offset > 0 -> "+$offset ms"
                offset < 0 -> "$offset ms"
                else -> "0 ms"
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
                        text = offsetFormatted,
                        color = offsetColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            offset > 0 -> "歌词延后推送"
                            offset < 0 -> "歌词提前推送"
                            else -> "无延迟微调"
                        },
                        color = offsetColor.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "−500" to -500L,
                    "−200" to -200L,
                    "重置" to 0L,
                    "+200" to 200L,
                    "+500" to 500L,
                ).forEach { (label, delta) ->
                    Button(
                        onClick = {
                            val newOffset = if (delta == 0L) 0L else (state.config.offsetMs + delta).coerceIn(-5000L, 5000L)
                            onConfigChanged(state.config.copy(offsetMs = newOffset))
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (delta == 0L) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    Section(title = "模块与系统") {
        SwitchPreference(
            checked = state.launcherIconHidden,
            onCheckedChange = onLauncherIconChanged,
            title = "隐藏桌面图标",
            summary = "隐藏后仍可从 LSPosed 的模块设置进入",
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(text = "重启系统界面", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(
                text = state.restartStatus.ifBlank { "需要 root 授权。重启期间状态栏和手势导航栏会短暂消失重载。" },
                color = if (state.restartStatus.startsWith("重启失败")) MiuixTheme.colorScheme.error
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(
                onClick = onRestartSystemUi,
                enabled = !state.restartLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (state.restartLoading) "正在重启…" else "重启 SystemUI")
            }
        }
    }

    Section(title = "歌词检索测试") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("三源检索测试", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(
                "依次检索 LRCLIB、网易云与酷狗，测试同步歌词匹配与车机显示效果。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            MiuixInput(value = title, onValueChange = onTitleChanged, label = "歌曲标题", placeholder = "例如：晴天")
            MiuixInput(value = artist, onValueChange = onArtistChanged, label = "歌手", placeholder = "例如：周杰伦（可选）")
            Button(
                onClick = onTestSearch,
                enabled = !state.testLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (state.testLoading) "检索中…" else "开始检索")
            }
            if (state.testResult.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (state.testError) MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else MiuixTheme.colorScheme.surfaceContainerHigh
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        state.testResult,
                        color = if (state.testError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }

    Section(title = "使用指引") {
        BasicComponent(title = "系统界面 (System UI)", summary = "在 LSPosed 作用域勾选「系统界面」，提供 HyperLyric 状态栏与超级岛歌词。")
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        BasicComponent(title = "YouTube Music", summary = "在 LSPosed 作用域勾选 YouTube Music，用于拦截播放并推送车载蓝牙歌词。")
    }
}

@Composable
private fun CacheContent(
    state: SettingsUiState,
    onCacheSearchChanged: (String) -> Unit,
    onClearCache: () -> Unit,
    onCacheClick: (LyricsCacheEntry) -> Unit,
    onDeleteCache: (LyricsCacheEntry) -> Unit,
) {
    Section(title = "本地歌词记录") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "已缓存的歌词与时间轴校准。点击歌曲可编辑歌词、微调时间轴或手动重新搜索。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            var search by remember { mutableStateOf(TextFieldValue(state.cacheSearch)) }
            LaunchedEffect(state.cacheSearch) {
                if (search.text != state.cacheSearch) search = TextFieldValue(state.cacheSearch)
            }
            TextField(
                value = search,
                onValueChange = {
                    search = it
                    onCacheSearchChanged(it.text)
                },
                label = "搜索歌名或歌手",
                useLabelAsPlaceholder = true,
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = "搜索",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                trailingIcon = {
                    if (search.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                search = TextFieldValue("")
                                onCacheSearchChanged("")
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Basic.SearchCleanup,
                                contentDescription = "清空",
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.cacheSearch.isBlank()) "共 ${state.cacheCount} 首歌曲"
                    else "找到 ${state.cacheCount} 首歌曲",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
                if (state.cacheCount > 0) {
                    Button(
                        onClick = onClearCache,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                    ) {
                        Text("清空全部", fontSize = 12.sp)
                    }
                }
            }
            if (state.cacheCount > state.cacheEntries.size) {
                Text(
                    "显示前 ${state.cacheEntries.size} 首，请通过搜索缩小范围",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (state.cacheEntries.isEmpty()) {
                Text(
                    if (state.cacheSearch.isBlank()) "暂无歌曲记录\n播放 YouTube Music 并检索歌词后，会自动保存在这里。"
                    else "没有匹配的歌曲\n试试其他歌名或歌手关键词。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                state.cacheEntries.forEach { entry ->
                    CacheEntryCard(entry, onClick = { onCacheClick(entry) }, onDelete = { onDeleteCache(entry) })
                }
            }
        }
    }
}

@Composable
private fun CacheEntryCard(entry: LyricsCacheEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(entry.artist.ifBlank { "未知歌手" })
                        if (entry.durationMs > 0) {
                            val totalSec = entry.durationMs / 1000
                            append(" · %02d:%02d".format(totalSec / 60, totalSec % 60))
                        }
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (entry.hasLyrics) MiuixTheme.colorScheme.primaryContainer
                                else MiuixTheme.colorScheme.errorContainer
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (entry.hasLyrics) entry.displaySource else "下载失败",
                            color = if (entry.hasLyrics) MiuixTheme.colorScheme.onPrimaryContainer
                            else MiuixTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            TextButton(
                text = "删除",
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.error,
                ),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SmallTitle(text = title)
    Card(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
internal fun MiuixInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    placeholder: String,
    multiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = "$label · $placeholder",
        useLabelAsPlaceholder = true,
        singleLine = !multiline,
        maxLines = if (multiline) 18 else 1,
        minLines = if (multiline) 12 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    )
}

