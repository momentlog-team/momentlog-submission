package team.momentlog.app.config.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 색 토큰. 낮/밤 두 벌을 두고 시스템 테마에 맞춰 고른다. */
@Immutable
data class MomentlogColors(
    val bg: Color,
    val surface: Color,
    val surfaceSunk: Color,
    val line: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val recording: Color,
    val warning: Color,
    val danger: Color,
    val road: Color,
    val roadMark: Color,
    val carBody: Color,
    val carEdge: Color,
    val dataSpeed: Color,
    val dataBrake: Color = colorF0505A,
    val dataTurn: Color = colorF5A524,
    val dataSteer: Color = color36C5D6,
    val isNight: Boolean,
)

val DayColors = MomentlogColors(
    bg = colorE9EDF1, surface = colorFFFFFF, surfaceSunk = colorDDE2E8, line = colorC9D0D8,
    textPrimary = color10151B, textSecondary = color56606C, textTertiary = color8A939E,
    accent = color2F6FEB, recording = color12A071, warning = colorC77A00, danger = colorE0313C,
    road = colorD3D9E0, roadMark = colorFFFFFF, carBody = colorFFFFFF, carEdge = colorB7C0CA,
    dataSpeed = color10151B, isNight = false,
)

val NightColors = MomentlogColors(
    bg = color1A1F25, surface = color252B33, surfaceSunk = color20262D, line = color3A424C,
    textPrimary = colorF1F3F6, textSecondary = colorA3ADB9, textTertiary = color737D89,
    accent = color5B92FF, recording = color2BC48A, warning = colorF5A524, danger = colorE0313C,
    road = color2A3038, roadMark = color4A535E, carBody = color3A424D, carEdge = color4B5562,
    dataSpeed = colorE8ECF1, isNight = true,
)

val LocalAppColors = staticCompositionLocalOf { DayColors }

/**
 * 화면 수치는 QHD 앱 창(가로 1650×1180, 세로 분할 824×1180)의 픽셀로 적고, 실제 창 크기에 맞춰 비율로 바꾼다.
 */
@Immutable
data class QhdFrame(val pxToDp: Float, val split: Boolean)

val LocalQhd = staticCompositionLocalOf { QhdFrame(pxToDp = 0.5f, split = false) }

/** QHD px → dp. */
val Int.q: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalQhd.current.pxToDp).dp

val Float.q: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalQhd.current.pxToDp).dp

/** QHD px → sp. 차량 화면은 배치가 고정이라 시스템 글자 크기 설정은 반영하지 않는다. */
val Int.qs: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalQhd.current.pxToDp / LocalDensity.current.fontScale).sp

/** 앱 2개를 나란히 띄운 세로 창인지. */
val isSplit: Boolean
    @Composable @ReadOnlyComposable get() = LocalQhd.current.split

/** 한국어는 단어 단위로 줄을 바꾼다. */
@Composable
fun textStyle(size: Int, lineHeight: Int? = null, weight: FontWeight = FontWeight.Normal, color: Color = LocalAppColors.current.textPrimary) =
    TextStyle(
        fontSize = size.qs,
        lineHeight = (lineHeight ?: (size * 1.4f).toInt()).qs,
        fontWeight = weight,
        color = color,
        lineBreak = LineBreak.Heading,
    )

@Composable
fun MomentlogTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (night) NightColors else DayColors
    val scheme = if (night) {
        darkColorScheme(primary = colors.accent, background = colors.bg, surface = colors.surface, onSurface = colors.textPrimary)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.bg, surface = colors.surface, onSurface = colors.textPrimary)
    }
    BoxWithConstraints {
        val split = maxWidth < maxHeight
        val designWidth = if (split) SPLIT_WIDTH else FULL_WIDTH
        val pxToDp = minOf(maxWidth.value / designWidth, maxHeight.value / DESIGN_HEIGHT)
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalQhd provides QhdFrame(pxToDp, split),
        ) {
            MaterialTheme(colorScheme = scheme, content = content)
        }
    }
}

private const val FULL_WIDTH = 1650f
private const val SPLIT_WIDTH = 824f
private const val DESIGN_HEIGHT = 1180f
