package team.momentlog.app.ui.screens.widget.car

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.app.ui.models.motionAt
import team.momentlog.core.Snapshot
import team.momentlog.core.TurnSignal
import team.momentlog.core.Trigger
import kotlin.math.sin
import kotlin.math.PI

/** 도로·차체·램프가 같은 재생 시각을 사용한다. 정지·일시정지에서는 도로의 움직임도 멈춘다. */
@Composable
fun DrivingPlaybackCar(record: RecordInfo, snapshot: Snapshot?, playhead: Long, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val start = maxOf(record.startMillis, record.triggeredAtMillis - 10_000)
    val motion = record.motionAt(playhead, start)
    val speed by animateFloatAsState(snapshot?.speedKph ?: 0f, tween(140), label = "driveSpeed")
    val steering by animateFloatAsState(snapshot?.steeringDeg ?: 0f, tween(140), label = "driveSteering")
    val heading by animateFloatAsState(motion.headingDegrees, tween(160), label = "driveHeading")
    val brake by animateFloatAsState(if (snapshot?.brake == true) 1f else 0f, tween(180), label = "brakeLight")
    val turn = snapshot?.turn
    val turnOn = turn == TurnSignal.LEFT || turn == TurnSignal.RIGHT
    val turnStart = if (turnOn) record.snapshots.asReversed().firstOrNull { it.atMillis <= playhead && it.turn != turn }?.atMillis ?: start else playhead
    // 깜빡임은 점등 신호를 시각화하는 효과이며, 실제 램프의 점멸 위상을 측정한 값은 아니다.
    val end = maxOf(record.triggeredAtMillis, record.events.filter { it.trigger == Trigger.AIRBAG }.maxOfOrNull { it.atMillis } ?: record.triggeredAtMillis).coerceAtMost(record.endMillis)
    val playbackScale = 6000f / (end - start).coerceAtLeast(1)
    val blinkPhase = ((playhead - turnStart).coerceAtLeast(0) * playbackScale % 900f) / 900f
    val pulse = if (blinkPhase < 0.52f) 1f else 0f
    val blink by animateFloatAsState(if (turnOn) pulse else 0f, tween(85), label = "turnBlink")
    val lamps = CarLamps(turnRight = turn == TurnSignal.RIGHT, turnLeft = turn == TurnSignal.LEFT,
        brake = brake > 0.01f, head = snapshot?.lowBeam == true || snapshot?.highBeam == true)
    val impactAt = record.events.firstOrNull { it.trigger == Trigger.AIRBAG }?.atMillis
    val impactReached = impactAt != null && playhead >= impactAt
    val impact = remember(record.id) { Animatable(0f) }
    LaunchedEffect(record.id, impactReached) {
        impact.snapTo(0f)
        if (impactReached) impact.animateTo(1f, tween(1150, easing = LinearEasing))
    }
    val shock = if (impactReached) (1f - impact.value).coerceIn(0f, 1f) else 0f

    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.65f; scaleY = 1.12f }.clipToBounds()) {
            val u = size.width / 200f
            val phase = (motion.distanceMeters * 11f) % 100f
            val visibility = (speed / 65f).coerceIn(0.3f, 0.7f)
            val road = colors.road
            drawRect(Brush.verticalGradient(0f to road.copy(alpha = 0f), 0.16f to road.copy(alpha = 0.72f), 0.84f to road.copy(alpha = 0.72f), 1f to road.copy(alpha = 0f)))
            val edge = Brush.verticalGradient(0f to colors.roadMark.copy(alpha = 0f), 0.2f to colors.roadMark.copy(alpha = 0.4f), 0.8f to colors.roadMark.copy(alpha = 0.4f), 1f to colors.roadMark.copy(alpha = 0f))
            for (x in listOf(4f, 196f)) drawLine(edge, Offset(x * u, 0f), Offset(x * u, size.height), 1.2f * u)
            for (x in listOf(18f, 182f)) {
                for (i in -2..6) {
                    val y = (i * 100 + phase) * u
                    drawLine(colors.roadMark.copy(alpha = visibility), Offset(x * u, y), Offset(x * u, y + 42 * u), 2 * u, StrokeCap.Round)
                }
            }
            if (speed > 1f) {
                for (x in listOf(22f, 178f)) {
                    val length = (speed / 65f).coerceIn(0f, 1f) * 90 * u
                    drawLine(colors.accent.copy(alpha = 0.12f), Offset(x * u, size.height * 0.6f), Offset(x * u, size.height * 0.6f + length), 4 * u, StrokeCap.Round)
                }
            }
        }
        CarDrawing(
            Modifier.fillMaxSize().graphicsLayer {
                val shake = sin(impact.value * PI.toFloat() * 12) * shock * shock
                rotationZ = heading + shake * 2f
                translationX = heading / 22f * size.width * 0.045f + shake * size.width * 0.035f
                translationY = -((speed / 65f).coerceIn(0f, 1f)) * size.height * 0.025f + brake * size.height * 0.01f
                scaleX = 1f - sin(impact.value * PI.toFloat()) * shock * 0.025f
                scaleY = scaleX
            },
            lamps, steeringDeg = steering, turnIntensity = blink, brakeIntensity = brake, impactIntensity = shock,
        )
        if (impactReached) {
            Canvas(Modifier.fillMaxSize()) {
                val p = impact.value
                repeat(2) { index ->
                    val wave = ((p - index * 0.16f) / 0.84f).coerceIn(0f, 1f)
                    if (wave > 0f && wave < 1f) {
                        val radius = size.width * (0.42f + wave * 0.55f)
                        drawCircle(colors.danger.copy(alpha = (1 - wave) * 0.36f), radius, Offset(size.width / 2, size.height / 2), style = Stroke(size.width * 0.012f))
                    }
                }
            }
        }
    }
}
