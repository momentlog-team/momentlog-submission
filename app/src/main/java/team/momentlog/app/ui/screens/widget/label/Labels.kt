package team.momentlog.app.ui.screens.widget.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    dot: Boolean = false,
    background: Color = color.copy(alpha = 0.14f),
) {
    Row(
        modifier.height(52.q).background(background, CircleShape).padding(horizontal = 22.q),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.q),
    ) {
        if (dot) Box(Modifier.size(14.q).background(color, CircleShape))
        if (icon != null) Icon(icon, 28.q, color)
        Text(text, style = textStyle(24, 24, FontWeight.Bold, color), maxLines = 1)
    }
}

@Composable
fun DriveFlag(drivingAllowed: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.q).background(if (drivingAllowed) colors.recording else colors.textTertiary, CircleShape))
        Spacer(Modifier.width(8.q))
        Text(if (drivingAllowed) "주행 중 표시 가능" else "정차 시에만", style = textStyle(18, 18, color = colors.textTertiary))
    }
}

/** 음성 안내. [bold]는 굵게 보여 줄 앞부분. */
@Composable
fun VoiceHint(bold: String?, rest: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcon.MIC, 36.q, colors.accent)
        Spacer(Modifier.width(14.q))
        Text(
            buildAnnotatedString {
                if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary)) { append(bold) }
                append(rest)
            },
            style = textStyle(30, 42, color = colors.textSecondary),
        )
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, size: Int = 24) {
    val colors = LocalAppColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcon.INFO, 30.q, colors.textTertiary)
        Spacer(Modifier.width(12.q))
        Text(text, style = textStyle(size, color = colors.textSecondary))
    }
}

/** 번호판. 밤 테마에서도 흰 바탕. */
@Composable
fun PlateBadge(plate: String, height: Int, fontSize: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.q)
    Box(
        modifier
            .height(height.q)
            .background(Color.White, shape)
            .border(3.q, Color(0xFF10151B), shape)
            .padding(horizontal = 28.q),
        contentAlignment = Alignment.Center,
    ) {
        Text(plate, style = textStyle(fontSize, fontSize, FontWeight.ExtraBold, Color(0xFF10151B)), maxLines = 1)
    }
}
