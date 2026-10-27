package team.momentlog.app.ui.screens.widget.car

import team.momentlog.app.ui.screens.widget.icon.svgPath

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.MomentlogColors

data class CarLamps(
    val turnRight: Boolean = false,
    val turnLeft: Boolean = false,
    val brake: Boolean = false,
    val head: Boolean = false,
)

/**
 * 위에서 본 내 차(좌표 200×400, 앞이 위). 테마에 따라 차체 색만 바뀐다. 크기는 1:2 비율로 준다.
 */
@Composable
fun CarDrawing(modifier: Modifier = Modifier, lamps: CarLamps = CarLamps(), steeringDeg: Float = 0f, turnIntensity: Float = 1f, brakeIntensity: Float = 1f, impactIntensity: Float = 0f) {
    val colors = LocalAppColors.current
    Canvas(modifier) {
        val k = size.width / 200f
        scale(k, pivot = Offset.Zero) {
            drawCar(colors, lamps, steeringDeg, turnIntensity, brakeIntensity)
            if (impactIntensity > 0f) {
                drawPath(bodyPath, colors.danger.copy(alpha = 0.18f * impactIntensity), style = Stroke(12f))
                drawPath(bodyPath, colors.danger.copy(alpha = 0.8f * impactIntensity), style = Stroke(2.5f))
            }
        }
    }
}

private val bodyPath by lazy {
    svgPath("M100 10 C142 10 165 22 171 50 L177 118 C180 168 180 250 178 300 L174 354 C171 380 150 392 100 392 C50 392 29 380 26 354 L22 300 C20 250 20 168 23 118 L29 50 C35 22 58 10 100 10 Z")
}
private val hoodPath by lazy { svgPath("M70 30 C66 62 64 92 62 122 M130 30 C134 62 136 92 138 122") }
private val windshieldPath by lazy { svgPath("M42 130 C66 113 134 113 158 130 L148 178 C120 170 80 170 52 178 Z") }
private val glarePath by lazy { svgPath("M58 126 C78 118 100 117 112 118 L98 132 C84 132 70 134 60 138 Z") }
private val roofPath by lazy { svgPath("M52 184 C78 178 122 178 148 184 L150 288 C122 294 78 294 50 288 Z") }
private val roofGlarePath by lazy { svgPath("M64 192 L92 190 L70 280 L60 280 Z") }
private val sideGlassPath by lazy { svgPath("M34 150 C36 200 36 260 34 318 M166 150 C164 200 164 260 166 318") }
private val rearGlassPath by lazy { svgPath("M52 296 C80 302 120 302 148 296 L154 334 C124 346 76 346 46 334 Z") }
private val mirrorPath by lazy {
    svgPath("M26 138 C16 134 7 140 8 149 C9 156 18 158 27 154 Z M174 138 C184 134 193 140 192 149 C191 156 182 158 173 154 Z")
}
private val headPath by lazy { svgPath("M38 42 C50 31 66 26 82 24 M162 42 C150 31 134 26 118 24") }
private val turnLeftPath by lazy { svgPath("M31 56 L36 46 M29 350 L33 360") }
private val turnRightPath by lazy { svgPath("M169 56 L164 46 M171 350 L167 360") }
private val brakePath by lazy { svgPath("M38 366 Q52 377 80 378 M162 366 Q148 377 120 378 M84 340h32") }

private val shadeBrush = Brush.horizontalGradient(
    0f to Color(0x380B0F14), 0.2f to Color(0x000B0F14), 0.5f to Color(0x1AFFFFFF), 0.8f to Color(0x000B0F14), 1f to Color(0x380B0F14),
    startX = 22f, endX = 178f,
)
private val glassBrush = Brush.verticalGradient(listOf(Color(0xFF3B4652), Color(0xFF1A2027)), startY = 115f, endY = 345f)
private val roofBrush = Brush.linearGradient(
    0f to Color(0xFF2E3742), 0.55f to Color(0xFF232A33), 1f to Color(0xFF1A2027),
    start = Offset(50f, 178f), end = Offset(150f, 294f),
)
private val wheel = Color(0xFF14181D)

private fun DrawScope.drawCar(c: MomentlogColors, lamps: CarLamps, steeringDeg: Float, turnIntensity: Float, brakeIntensity: Float) {
    drawOval(
        Brush.radialGradient(0.6f to Color(0x380B0F14), 1f to Color(0x000B0F14), center = Offset(100f, 206f), radius = 200f),
        topLeft = Offset(2f, 6f), size = Size(196f, 400f),
    )
    listOf(15f to 84f, 171f to 84f, 15f to 282f, 171f to 282f).forEach { (x, y) ->
        val angle = if (y < 200) (steeringDeg / 15f).coerceIn(-36f, 36f) else 0f
        rotate(angle, Offset(x + 7f, y + 27f)) {
            drawRoundRect(wheel, Offset(x, y), Size(14f, if (y > 200) 56f else 54f), CornerRadius(6f))
        }
    }
    drawPath(bodyPath, c.carBody)
    drawPath(bodyPath, shadeBrush)
    drawPath(bodyPath, c.carEdge, style = Stroke(1.5f))
    drawPath(hoodPath, c.carEdge.copy(alpha = 0.7f), style = Stroke(1.5f))
    drawPath(windshieldPath, glassBrush)
    drawPath(glarePath, Color.White.copy(alpha = 0.12f))
    drawPath(roofPath, roofBrush)
    drawPath(roofGlarePath, Color.White.copy(alpha = 0.06f))
    drawPath(sideGlassPath, Color(0x8C2A323C), style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(rearGlassPath, glassBrush)
    drawPath(mirrorPath, c.carBody)
    drawPath(mirrorPath, c.carEdge, style = Stroke(1.5f))
    // 꺼진 램프
    drawPath(headPath, Color(0xFFC9D3DE), style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(turnLeftPath, c.carEdge, style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(turnRightPath, c.carEdge, style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(brakePath, c.carEdge.copy(alpha = 0.7f), style = Stroke(4f, cap = StrokeCap.Round))
    // 켜진 램프
    if (lamps.head) drawPath(headPath, Color(0xFFF4F8FF), style = Stroke(6f, cap = StrokeCap.Round))
    listOf(turnLeftPath to lamps.turnLeft, turnRightPath to lamps.turnRight).forEach { (path, on) ->
        if (on) {
            drawPath(path, c.dataTurn.copy(alpha = 0.16f * turnIntensity), style = Stroke(14f, cap = StrokeCap.Round))
            drawPath(path, c.dataTurn.copy(alpha = turnIntensity), style = Stroke(6f, cap = StrokeCap.Round))
            drawPath(path, Color(0xFFFFE3A1).copy(alpha = 0.8f * turnIntensity), style = Stroke(2f, cap = StrokeCap.Round))
        }
    }
    if (lamps.brake) {
        drawPath(brakePath, c.dataBrake.copy(alpha = 0.12f * brakeIntensity), style = Stroke(12f, cap = StrokeCap.Round))
        drawPath(brakePath, c.dataBrake.copy(alpha = brakeIntensity), style = Stroke(5f, cap = StrokeCap.Round))
        drawPath(brakePath, Color(0xFFFFA7AD).copy(alpha = 0.7f * brakeIntensity), style = Stroke(1.5f, cap = StrokeCap.Round))
    }
}
