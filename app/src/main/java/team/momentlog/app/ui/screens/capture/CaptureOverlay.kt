package team.momentlog.app.ui.screens.capture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.models.Formats
import team.momentlog.app.ui.models.RecordKind
import team.momentlog.app.ui.models.relativeText
import team.momentlog.core.IncidentEvent
import team.momentlog.core.Trigger
import team.momentlog.app.ui.screens.widget.car.ImpactArc
import team.momentlog.app.ui.screens.widget.car.RoadGeometry
import team.momentlog.app.ui.screens.widget.car.RoadStage
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.label.VoiceHint
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.motion.EaseOut
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow

/**
 * 저장 알림. 주행 중에 보이는 화면이라 버튼은 정차 중 저장일 때만 둔다.
 */
@Composable
fun CaptureOverlay(
    kind: RecordKind,
    atMillis: Long,
    parked: Boolean,
    events: List<IncidentEvent>,
    onSpeak: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = LocalAppColors.current
    val split = isSplit
    val tone = kind.tone(colors)
    val followUps = events.filter { it.atMillis > atMillis }
    val earlier = events.filter { it.atMillis < atMillis }
    val copy = if (followUps.isEmpty()) CaptureCopy.of(kind) else CaptureCopy.followUp(kind)

    AppScreen(drivingAllowed = kind != RecordKind.AFTER_STOP) {
        RoadStage(
            parked = parked,
            behindCar = { g -> SavedSpan(g, tone, kind, copy, atMillis, earlier, followUps) },
            overCar = { g -> if (kind == RecordKind.IMPACT) ImpactArc(rear = true, g = g) },
        )

        Column(
            (if (split) Modifier.padding(start = 360.q, end = 48.q, top = 120.q) else Modifier.fillMaxHeight().padding(start = 820.q, end = 80.q))
                .enterFromBelow(16.q, 120),
            verticalArrangement = if (split) Arrangement.Top else Arrangement.Center,
        ) {
            StatusChip(copy.chip, tone, icon = copy.icon)
            Spacer(Modifier.height(32.q))
            Text(copy.title, style = textStyle(if (split) 48 else 60, if (split) 64 else 80, FontWeight.ExtraBold))
            Spacer(Modifier.height(16.q))
            Text(copy.sub, style = textStyle(30, 42, color = colors.textSecondary))
            Spacer(Modifier.height(40.q))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Formats.clock(atMillis), style = textStyle(48, 56, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(16.q))
                Text(
                    if (followUps.isEmpty()) Formats.date(atMillis) else "첫 감지 ${Formats.clock(atMillis)}",
                    style = textStyle(24, 40, color = colors.textSecondary),
                )
            }
        }

        val bottomModifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = if (split) 360.q else 820.q, end = if (split) 48.q else 80.q, bottom = if (split) 80.q else 88.q)
            .enterFromBelow(16.q, 120)
        if (kind == RecordKind.AFTER_STOP) {
            val actions: @Composable (Modifier) -> Unit = { m ->
                CustomButton("상황 말하기", onSpeak, m, primary = true, icon = AppIcon.MIC)
                CustomButton("나중에", onLater, m)
            }
            if (split) {
                Column(bottomModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.q)) { actions(Modifier.fillMaxWidth()) }
            } else {
                Row(bottomModifier, horizontalArrangement = Arrangement.spacedBy(16.q)) { actions(Modifier) }
            }
        } else {
            VoiceHint(null, copy.voice, bottomModifier)
        }
    }
}

private data class CaptureCopy(
    val chip: String,
    val icon: AppIcon,
    val title: String,
    val sub: String,
    val endLabel: String,
    val pendingLabel: String?,
    val voice: String,
) {
    companion object {
        fun followUp(kind: RecordKind) = of(kind).copy(
            chip = "같은 기록에 이어 붙임",
            title = "한 번 더 감지해 이어서 저장해요",
            sub = "새 기록을 만들지 않고 앞 기록에 붙였어요",
        )

        fun of(kind: RecordKind) = when (kind) {
            RecordKind.HARD_STOP -> CaptureCopy(
                "급정지 감지", AppIcon.STOP, "급정지를 감지해 저장했어요", "직전 30초부터 차가 멈추고 10초 뒤까지 남겨요",
                "−30초", "멈춘 뒤 10초", "차가 멈추면 상황을 여쭤볼게요",
            )
            RecordKind.IMPACT -> CaptureCopy(
                "충격 감지 · 뒤쪽", AppIcon.IMPACT, "충격을 감지해 저장했어요", "직전 30초부터 차가 멈추고 10초 뒤까지 남겨요",
                "−30초", "멈춘 뒤 10초", "차가 멈추면 상황을 여쭤볼게요",
            )
            RecordKind.VOICE -> CaptureCopy(
                "“기록해”", AppIcon.MIC, "말씀하신 순간을 저장했어요", "직전 3분부터 말한 뒤 30초까지 남겨요",
                "−3분", "+30초", "휴게소 등에 멈추면 상황을 여쭤볼게요",
            )
            RecordKind.AFTER_STOP -> CaptureCopy(
                "정차 중 저장", AppIcon.REC, "방금 일을 저장했어요", "감지되지 않았던 직전 3분을 남겼어요",
                "−3분", null, "",
            )
        }
    }
}

/**
 * 도로 위 저장 구간. 차 뒤쪽은 저장된 구간, 차 앞쪽은 아직 저장 중인 구간.
 */
@Composable
private fun BoxScope.SavedSpan(
    g: RoadGeometry,
    tone: Color,
    kind: RecordKind,
    copy: CaptureCopy,
    atMillis: Long,
    earlier: List<IncidentEvent>,
    followUps: List<IncidentEvent>,
) {
    val colors = LocalAppColors.current
    val split = isSplit
    val shutter = remember { Animatable(0f) }
    val labels = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shutter.animateTo(1f, tween(700, easing = EaseOut)) }
    LaunchedEffect(Unit) { labels.animateTo(1f, tween(400, 360, EaseOut)) }
    val unit = 1.q
    val savedTop = g.carRearY - 4
    val pendingTop = 24
    val endInset = 40

    Canvas(Modifier.fillMaxSize()) {
        val u = unit.toPx()
        val roadL = g.roadX * u
        val roadW = g.roadWidth * u
        val bottom = size.height - endInset * u
        val fill = lerp(colors.accent, tone, shutter.value).copy(alpha = 0.22f)
        drawRect(fill, Offset(roadL, savedTop * u), Size(roadW, bottom - savedTop * u))
        val widen = (1.35f - 0.35f * shutter.value)
        val lineW = (roadW + 20 * u) * widen
        val lineL = roadL + roadW / 2 - lineW / 2
        listOf(savedTop * u, bottom).forEach { y -> drawRect(tone, Offset(lineL, y - 3 * u), Size(lineW, 6 * u)) }
        if (copy.pendingLabel != null) {
            drawRoundRect(
                tone.copy(alpha = 0.7f), Offset(roadL, pendingTop * u), Size(roadW, (g.carRearY - pendingTop) * u),
                CornerRadius(12 * u), style = Stroke(4 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14 * u, 10 * u))),
            )
        }
        // 차 뒤 범퍼 = 0초
        drawLine(
            colors.textPrimary.copy(alpha = labels.value), Offset(roadL - 14 * u, g.carRearY * u), Offset(roadL + roadW + 14 * u, g.carRearY * u),
            4 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * u, 8 * u)),
        )
    }

    val labelX = if (split) 56.q else 590.q
    val raise = if (split) 36 else 0
    Column(Modifier.offset(labelX, (g.carRearY - 46 - raise).q).alpha(labels.value)) {
        Text("0초 · ${kind.markerLabel}", style = textStyle(24, 32, FontWeight.Bold))
    }
    if (copy.pendingLabel != null) {
        Column(Modifier.offset(labelX, (pendingTop + 8).q).alpha(labels.value)) {
            Text(copy.pendingLabel, style = textStyle(24, 30, FontWeight.Bold, tone))
            Text("저장 중", style = textStyle(20, 26, color = colors.textSecondary))
        }
    }
    // 합친 앞선 기록의 감지는 저장된 구간 안에 시간 비율로 놓는다.
    val savedBottom = DESIGN_HEIGHT - endInset
    val preMillis = (if (kind == RecordKind.VOICE || kind == RecordKind.AFTER_STOP) Trigger.VOICE else Trigger.HARD_STOP).window.preMillis
    earlier.forEach { e ->
        val ratio = ((atMillis - e.atMillis).toFloat() / preMillis).coerceIn(0f, 1f)
        val y = (g.carRearY + ratio * (savedBottom - g.carRearY)).toInt()
        EventMark(g, y, "${relativeText((e.atMillis - atMillis) / 1000f)} · ${RecordKind.of(e).markerLabel}", "앞서 저장된 기록을 합쳤어요", labelX, labels.value)
    }
    followUps.forEachIndexed { i, e ->
        EventMark(g, g.carY - 40 - i * 72, "${relativeText((e.atMillis - atMillis) / 1000f)} · ${RecordKind.of(e).markerLabel}", null, labelX, labels.value)
    }
    Text(
        copy.endLabel,
        style = textStyle(24, 30, FontWeight.Bold, tone),
        modifier = Modifier.align(Alignment.BottomStart).padding(start = labelX, bottom = (endInset + 8 + raise).q).alpha(labels.value),
    )
}

/** 앱 창 높이(QHD px). */
private const val DESIGN_HEIGHT = 1180

@Composable
private fun EventMark(g: RoadGeometry, y: Int, label: String, note: String?, labelX: Dp, alpha: Float) {
    val colors = LocalAppColors.current
    val unit = 1.q
    Canvas(Modifier.fillMaxSize().alpha(alpha)) {
        val u = unit.toPx()
        drawLine(
            colors.textPrimary, Offset((g.roadX - 14) * u, y * u), Offset((g.roadX + g.roadWidth + 14) * u, y * u),
            3 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * u, 8 * u)),
        )
    }
    Column(Modifier.offset(labelX, (y - 40).q).alpha(alpha)) {
        Text(label, style = textStyle(24, 30, FontWeight.Bold))
        if (note != null) Text(note, style = textStyle(20, 26, color = colors.textSecondary))
    }
}
