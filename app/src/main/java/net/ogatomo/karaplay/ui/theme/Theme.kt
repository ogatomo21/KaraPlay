package net.ogatomo.karaplay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ご指定のデザインルール
private val ColorPrimary = Color(0xFF7086BD)
private val ColorSecondary = Color(0xFF435071)
private val ColorTertiary = Color(0xFFE2E5F1)
private val ColorDanger = Color(0xFFEB2323)
private val ColorLight = Color(0xFF237AEB)
private val ColorTextPrimary = Color(0xFF333333)
private val ColorWhite = Color(0xFFFFFFFF)

// ライトモード：白背景をベースに、PrimaryとTertiaryで爽やかに
private fun lightColors() = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorWhite,
    primaryContainer = ColorTertiary, // スライダーの非アクティブ部分などで紫になるのを防ぐ
    onPrimaryContainer = ColorSecondary,

    secondary = ColorSecondary,
    onSecondary = ColorWhite,
    secondaryContainer = ColorTertiary,
    onSecondaryContainer = ColorTextPrimary,

    tertiary = ColorLight,
    onTertiary = ColorWhite,
    tertiaryContainer = ColorTertiary,
    onTertiaryContainer = ColorLight,

    error = ColorDanger,
    onError = ColorWhite,
    errorContainer = ColorDanger.copy(alpha = 0.1f),
    onErrorContainer = ColorDanger,

    background = ColorWhite,
    onBackground = ColorTextPrimary,

    surface = ColorWhite,
    onSurface = ColorTextPrimary,
    surfaceVariant = ColorTertiary, // カードやダイアログの背景
    onSurfaceVariant = ColorSecondary,
    surfaceTint = ColorPrimary, // コンポーネントの影やハイライト色を上書き

    outline = ColorPrimary,
    outlineVariant = ColorTertiary
)

// ダークモード：TextPrimary(#333333)を背景色として逆利用し、指定色のみで構成
private fun darkColors() = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorWhite,
    primaryContainer = ColorSecondary, // ダークモード時のスライダー軌道色などを完全制御
    onPrimaryContainer = ColorWhite,

    secondary = ColorSecondary,
    onSecondary = ColorWhite,
    secondaryContainer = ColorSecondary,
    onSecondaryContainer = ColorWhite,

    tertiary = ColorLight,
    onTertiary = ColorWhite,
    tertiaryContainer = ColorSecondary,
    onTertiaryContainer = ColorWhite,

    error = ColorDanger,
    onError = ColorWhite,
    errorContainer = ColorDanger.copy(alpha = 0.2f),
    onErrorContainer = ColorWhite,

    background = ColorTextPrimary, // 濃いグレー(#333333)を背景に抜擢
    onBackground = ColorTertiary,  // 薄いブルーグレー(#E2E5F1)でテキストを読みやすく

    surface = ColorTextPrimary,
    onSurface = ColorTertiary,
    surfaceVariant = ColorSecondary, // カード背景には深みのあるSecondary(#435071)を使用
    onSurfaceVariant = ColorWhite,
    surfaceTint = ColorPrimary,

    outline = ColorTertiary,
    outlineVariant = ColorSecondary
)

@Composable
fun OgaTomoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors() else lightColors(),
        content = content
    )
}