package moe.lance.ytmusiclrc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

/** Shared HyperOS / Miuix palette for every user-facing screen in the module. */
@Composable
internal fun HyperLyricTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MiuixTheme(
        colors = if (dark) {
            darkColorScheme(
                primary = Color(0xFF3D8FFF),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF16325C),
                onPrimaryContainer = Color(0xFFD6E4FF),
                error = Color(0xFFFF6B6B),
                errorContainer = Color(0xFF5C1414),
                onError = Color(0xFFFFFFFF),
                onErrorContainer = Color(0xFFFFDADA),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF007DFF),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE5F1FF),
                onPrimaryContainer = Color(0xFF004BAA),
                error = Color(0xFFE53935),
                errorContainer = Color(0xFFFFEBEE),
                onError = Color(0xFFFFFFFF),
                onErrorContainer = Color(0xFFB71C1C),
            )
        },
        content = content,
    )
}

/** Standard Miuix / HyperOS section header for cards. */
@Composable
internal fun MiuixSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false,
) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = if (isFirst) 8.dp else 22.dp, bottom = 8.dp),
    )
}

