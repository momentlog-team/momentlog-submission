package team.momentlog.app.ui.screens.widget.car

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.screens.widget.motion.EaseOut
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow

/**
 * 도로와 차의 좌표(QHD px). 화면마다 라벨이나 저장 구간을 이 좌표 기준으로 덧그린다.
 */
data class RoadGeometry(
    val stageWidth: Int,
    val roadX: Int,
    val roadWidth: Int,
    val edgeLeftX: Int,
    val edgeRightX: Int,
    val laneX: Int,
    val carX: Int,
    val carY: Int,
    val carWidth: Int,
    val parkX: Int,
    val parkY: Int,
    val trailTop: Int,
) {
    val carHeight: Int get() = carWidth * 2
    val carRearY: Int get() = carY + carHeight

    companion object {
        val Full = RoadGeometry(780, 230, 320, 230, 546, 388, 280, 170, 220, 358, 360, 620)
        val Split = RoadGeometry(340, 40, 260, 40, 296, 168, 80, 240, 180, 138, 390, 640)
    }
}

val roadGeometry: RoadGeometry
    @Composable get() = if (isSplit) RoadGeometry.Split else RoadGeometry.Full

/**
 * 도로 위 내 차. [behindCar]는 차 아래에, [overCar]는 차 위에 그린다.
 */
@Composable
fun RoadStage(
    parked: Boolean,
    modifier: Modifier = Modifier,
    lamps: CarLamps = CarLamps(),
    dim: Boolean = false,
    behindCar: @Composable BoxScope.(RoadGeometry) -> Unit = {},
    overCar: @Composable BoxScope.(RoadGeometry) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val g = roadGeometry
    val stageAlpha by animateFloatAsState(if (dim) 0.45f else 1f, tween(420, easing = EaseOut), label = "dim")

    Box(modifier.width(g.stageWidth.q).fillMaxHeight().alpha(stageAlpha)) {
        Box(Modifier.offset(g.roadX.q).width(g.roadWidth.q).fillMaxHeight().background(colors.road))
        Box(Modifier.offset(g.edgeLeftX.q).width(4.q).fillMaxHeight().background(colors.roadMark))
        Box(Modifier.offset(g.edgeRightX.q).width(4.q).fillMaxHeight().background(colors.roadMark))
        val dash = 56.q
        Canvas(Modifier.offset(g.laneX.q).width(4.q).fillMaxHeight()) {
            val on = dash.toPx()
            drawLine(
                colors.roadMark.copy(alpha = 0.9f),
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, on)),
            )
        }

        behindCar(g)

        CarDrawing(
            Modifier
                .offset(g.carX.q, g.carY.q)
                .size(g.carWidth.q, g.carHeight.q)
                .enterFromBelow(48.q),
            lamps,
        )

        val parkScale by animateFloatAsState(if (parked) 1f else 0.6f, tween(420, easing = EaseOut), label = "p")
        val parkAlpha by animateFloatAsState(if (parked) 1f else 0f, tween(300, easing = EaseOut), label = "pAlpha")
        Box(
            Modifier
                .offset(g.parkX.q, g.parkY.q)
                .size(64.q)
                .scale(parkScale)
                .alpha(parkAlpha)
                .background(colors.textPrimary, RoundedCornerShape(16.q)),
            contentAlignment = Alignment.Center,
        ) {
            Text("P", style = textStyle(36, 36, FontWeight.ExtraBold, colors.bg))
        }

        overCar(g)
    }
}

/** 메모리에 들고 있는 최근 3분. */
@Composable
fun BoxScope.TrailBehindCar(g: RoadGeometry) {
    val colors = LocalAppColors.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, 160, EaseOut)) }
    Canvas(Modifier.offset(g.roadX.q, g.trailTop.q).width(g.roadWidth.q).fillMaxHeight()) {
        val h = size.height * reveal.value
        drawRect(
            Brush.verticalGradient(
                0f to colors.accent.copy(alpha = 0.24f), 0.92f to colors.accent.copy(alpha = 0f),
                endY = size.height,
            ),
            size = Size(size.width, h),
        )
    }
}

/** 차 뒤 범퍼 또는 앞 왼쪽 모서리를 감싸는 충격 호. */
@Composable
fun ImpactArc(rear: Boolean, g: RoadGeometry, strokePx: Int = 8) {
    val colors = LocalAppColors.current
    val w = g.carWidth * 180 / 220
    val stroke = strokePx.q
    if (rear) {
        Canvas(Modifier.offset((g.carX + (g.carWidth - w) / 2).q, (g.carRearY - 44 * g.carWidth / 220).q).size(w.q, (60 * g.carWidth / 220).q)) {
            drawArc(colors.danger, 20f, 140f, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
        }
    } else {
        Canvas(Modifier.offset((g.carX - 24).q, (g.carY - 24).q).size((g.carWidth / 2).q, (g.carWidth / 2).q)) {
            drawArc(colors.danger, 180f, 90f, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
        }
    }
}
