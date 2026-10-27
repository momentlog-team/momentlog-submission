package team.momentlog.app.ui.screens.widget.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** 빠르게 시작해 천천히 멈춤. */
val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * 아래에서 [offset]만큼 올라오며 나타난다. 한 번만 움직이고, [key]가 바뀌면 다시 재생한다.
 */
fun Modifier.enterFromBelow(offset: Dp, delayMillis: Int = 0, durationMillis: Int = 520, key: Any? = Unit): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(durationMillis, delayMillis, EaseOut)) }
    val px = with(LocalDensity.current) { offset.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * px
    }
}
