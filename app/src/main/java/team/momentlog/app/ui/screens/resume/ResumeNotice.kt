package team.momentlog.app.ui.screens.resume

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon
import team.momentlog.app.ui.screens.widget.motion.EaseOut

enum class ResumeReason { SCREEN, STATEMENT }

/**
 * 출발해서 정차 중 화면을 닫았다는 안내. 주행 중이라 버튼 없이 4초 뒤 사라진다.
 */
@Composable
fun ResumeNotice(reason: ResumeReason, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val (title, sub) = when {
        reason == ResumeReason.STATEMENT -> "말씀하신 데까지 저장했어요" to "멈추면 이어서 말씀하실 수 있어요"
        isSplit -> "기록 화면을 닫았어요" to "멈추면 다시 볼 수 있어요"
        else -> "출발해서 기록 화면을 닫았어요" to "기록은 저장되어 있고, 멈추면 다시 볼 수 있어요"
    }
    val drop = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(reason) {
        drop.animateTo(1f, tween(520, 520, EaseOut))
        delay(4_000)
        fade.animateTo(0f, tween(600))
        onFinished()
    }
    val shape = RoundedCornerShape(24.q)
    Row(
        modifier
            .padding(top = 40.q)
            .graphicsLayer {
                translationY = (1f - drop.value) * -size.height * 1.8f
                alpha = fade.value * drop.value.coerceAtLeast(0.01f)
            }
            .shadow(16.q, shape, ambientColor = Color(0x3D10151B), spotColor = Color(0x3D10151B))
            .background(colors.textPrimary, shape)
            .padding(horizontal = 40.q, vertical = 24.q),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcon.CHECK, 40.q, colors.recording)
        Spacer(Modifier.width(20.q))
        Column {
            Text(title, style = textStyle(30, 38, FontWeight.Bold, colors.bg))
            Text(sub, style = textStyle(24, 32, color = colors.bg.copy(alpha = 0.75f)))
        }
    }
}
