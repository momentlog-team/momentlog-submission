package team.momentlog.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon
import team.momentlog.app.ui.screens.widget.motion.EaseOut

/**
 * 차 화면용 버튼. 누르면 살짝 줄어든다.
 */
@Composable
fun CustomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    large: Boolean = true,
    icon: AppIcon? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120, easing = EaseOut), label = "press")
    val shape = RoundedCornerShape((if (compact) 18 else 20).q)
    val content = if (primary) Color.White else colors.textPrimary

    Row(
        modifier
            .alpha(if (enabled) 1f else 0.55f)
            .scale(scale)
            .height((if (compact) 80 else if (large) 100 else 80).q)
            .shadow(if (primary) 6.q else 3.q, shape, ambientColor = Color(0x2910151B), spotColor = Color(0x2910151B))
            .background(if (primary) colors.accent else colors.surface, shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = (if (compact) 32 else if (large) 52 else 40).q),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, (if (compact) 30 else 36).q, content)
            Spacer(Modifier.width((if (compact) 10 else 14).q))
        }
        Text(text, style = textStyle(if (compact) 26 else 30, 30, FontWeight.Bold, content), textAlign = TextAlign.Center, maxLines = 1)
    }
}
