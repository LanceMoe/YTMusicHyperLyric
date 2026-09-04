package moe.lance.ytmusiclyric.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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
