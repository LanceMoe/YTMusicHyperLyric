package moe.lance.ytmusiclyric.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
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
    Section(title = "车载蓝牙歌词", isFirst = true) {
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
                val steps = listOf(
                    Triple("−", "500", -500L),
                    Triple("−", "200", -200L),
                    Triple(null, "重置", 0L),
                    Triple("+", "200", 200L),
                    Triple("+", "500", 500L),
                )
                steps.forEach { (sign, value, delta) ->
                    Button(
                        onClick = {
                            val newOffset = if (delta == 0L) 0L else (state.config.offsetMs + delta).coerceIn(-5000L, 5000L)
                            onConfigChanged(state.config.copy(offsetMs = newOffset))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        minWidth = 0.dp,
                        minHeight = 0.dp,
                        insideMargin = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        colors = if (delta == 0L) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
                    ) {
                        if (sign != null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = sign,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 13.sp,
                                )
                                Text(
                                    text = value,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 12.sp,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = value,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
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

    Section(title = "关于") {
        AboutContent()
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
    var search by remember { mutableStateOf(TextFieldValue(state.cacheSearch)) }
    LaunchedEffect(state.cacheSearch) {
        if (search.text != state.cacheSearch) search = TextFieldValue(state.cacheSearch)
    }

    MiuixSearchBar(
        value = search,
        onValueChange = {
            search = it
            onCacheSearchChanged(it.text)
        },
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )

    MiuixSectionTitle(
        text = if (state.cacheSearch.isBlank()) "全部缓存 (${state.cacheCount})" else "搜索结果 (${state.cacheCount})",
        isFirst = true,
    )

    if (state.cacheEntries.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (state.cacheSearch.isBlank()) MiuixIcons.Basic.Search else MiuixIcons.Basic.SearchCleanup,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = if (state.cacheSearch.isBlank()) "暂无歌词缓存" else "未找到匹配歌曲",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = if (state.cacheSearch.isBlank())
                        "在 YouTube Music 播放歌曲并检索歌词后，将自动保存在这里供离线使用。"
                    else
                        "请检查歌名或歌手关键字拼写是否正确。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.cacheEntries.forEachIndexed { index, entry ->
                    CacheEntryRow(
                        entry = entry,
                        onClick = { onCacheClick(entry) },
                        onDelete = { onDeleteCache(entry) },
                    )
                    if (index < state.cacheEntries.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        if (state.cacheCount > state.cacheEntries.size) {
            Text(
                text = "显示前 ${state.cacheEntries.size} 首歌曲，可通过上方搜索框快速定位",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
            )
        }
    }

    if (state.cacheSearch.isBlank() && state.cacheCount > 0) {
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onClearCache,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            minWidth = 0.dp,
            minHeight = 0.dp,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.errorContainer,
                contentColor = MiuixTheme.colorScheme.error,
            ),
        ) {
            Text("清空全部本地歌词缓存", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MiuixSearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = "搜索",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        text = "搜索歌名或歌手…",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.text.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange(TextFieldValue("")) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = "清空搜索",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheEntryRow(entry: LyricsCacheEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(
                text = entry.title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val (badgeBg, badgeText) = when {
                    !entry.hasLyrics -> MiuixTheme.colorScheme.errorContainer to MiuixTheme.colorScheme.error
                    entry.source.contains("网易") || entry.source.contains("Netease", ignoreCase = true) ->
                        Color(0xFFE8F3FF) to Color(0xFF007DFF)
                    entry.source.contains("酷狗") || entry.source.contains("Kugou", ignoreCase = true) ->
                        Color(0xFFE1F8F6) to Color(0xFF009688)
                    else -> MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (entry.hasLyrics) entry.displaySource else "无歌词",
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.SearchCleanup,
                    contentDescription = "删除缓存",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                    modifier = Modifier.size(17.dp),
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "查看详情",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, isFirst: Boolean = false, content: @Composable () -> Unit) {
    MiuixSectionTitle(text = title, isFirst = isFirst)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutContent() {
    val context = LocalContext.current
    val packageInfo = remember(context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
    }
    val rawVersionName = packageInfo?.versionName ?: "0.2.0"
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            it.versionCode.toLong()
        }
    } ?: 1L
    val versionDisplay = if (rawVersionName.startsWith("v", ignoreCase = true)) {
        "$rawVersionName ($versionCode)"
    } else {
        "v$rawVersionName ($versionCode)"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AboutLineRow(
            annotatedText = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    append("作者：")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.5.sp,
                        color = MiuixTheme.colorScheme.primary,
                    ),
                ) {
                    append("@LanceMoe")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                ) {
                    append("（https://github.com/LanceMoe）")
                }
            },
            isLink = true,
            onClick = {
                openBrowser(context, "https://github.com/LanceMoe")
            },
            onLongClick = {
                copyToClipboard(context, "https://github.com/LanceMoe", "已复制作者主页链接")
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        AboutLineRow(
            annotatedText = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    append("GitHub仓库：")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary,
                    ),
                ) {
                    append("https://github.com/LanceMoe/YTMusicHyperLyric")
                }
            },
            isLink = true,
            onClick = {
                openBrowser(context, "https://github.com/LanceMoe/YTMusicHyperLyric")
            },
            onLongClick = {
                copyToClipboard(context, "https://github.com/LanceMoe/YTMusicHyperLyric", "已复制仓库链接")
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        AboutLineRow(
            annotatedText = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    append("版本信息：")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    ),
                ) {
                    append(versionDisplay)
                }
            },
            isLink = false,
            onClick = {
                copyToClipboard(context, "YouTube Music HyperLyric $versionDisplay", "已复制版本信息：$versionDisplay")
            },
            onLongClick = {
                copyToClipboard(context, "YouTube Music HyperLyric $versionDisplay", "已复制版本信息：$versionDisplay")
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutLineRow(
    annotatedText: androidx.compose.ui.text.AnnotatedString,
    isLink: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = annotatedText,
                lineHeight = 19.sp,
            )
        }
        if (isLink) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = "打开链接",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        copyToClipboard(context, url, "无法打开浏览器，已复制链接到剪贴板")
    }
}

private fun copyToClipboard(context: Context, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("text", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}


