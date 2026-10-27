package team.momentlog.app.ui.screens.widget.car

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.Size
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
    // 감쇠 스프링은 신호가 갱신되어도 이전 프레임의 속도를 이어 받아 움직인다.
    val speed by animateFloatAsState(snapshot?.speedKph ?: 0f, spring(dampingRatio = 1f, stiffness = 350f), label = "driveSpeed")
    val steering by animateFloatAsState(snapshot?.steeringDeg ?: 0f, spring(dampingRatio = 1f, stiffness = 280f), label = "driveSteering")
    val heading by animateFloatAsState(motion.headingDegrees, spring(dampingRatio = 1f, stiffness = 240f), label = "driveHeading")
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
            val phase = (motion.distanceMeters * 11f) % 88f
            val road = colors.road
            fun faded(color: androidx.compose.ui.graphics.Color, alpha: Float) = Brush.verticalGradient(
                0f to color.copy(alpha = 0f), 0.18f to color.copy(alpha = alpha),
                0.78f to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f),
            )
            // 중앙 차로와 양쪽 갓길을 낮은 대비로 분리한다. 모든 표식은 화면 끝에서 서서히 사라진다.
            drawRect(faded(road, 0.8f))
            for (x in listOf(0f, 186f)) {
                drawRect(faded(colors.roadMark, 0.035f), Offset(x * u, 0f), Size(14 * u, size.height))
            }
            for (x in listOf(14f, 186f)) {
                drawLine(faded(colors.roadMark, 0.28f), Offset(x * u, 0f), Offset(x * u, size.height), 0.8f * u)
                val inset = if (x < 100) x + 3f else x - 3f
                drawLine(faded(colors.roadMark, 0.07f), Offset(inset * u, 0f), Offset(inset * u, size.height), 0.6f * u)
            }
            for (x in listOf(42f, 158f)) {
                for (i in -1..(size.height / (88 * u)).toInt() + 1) {
                    val y = (i * 88 + phase) * u
                    drawLine(faded(colors.roadMark, 0.4f), Offset(x * u, y), Offset(x * u, y + 30 * u), 1.6f * u, StrokeCap.Butt)
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
