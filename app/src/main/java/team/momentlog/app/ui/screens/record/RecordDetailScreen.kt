package team.momentlog.app.ui.screens.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.MomentlogColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.models.Formats
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.app.ui.models.RecordKind
import team.momentlog.app.ui.models.secondsText
import team.momentlog.app.ui.models.steeringText
import team.momentlog.app.ui.models.turnSideText
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.widget.car.CarDrawing
import team.momentlog.app.ui.screens.widget.car.CarLamps
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.label.PlateBadge
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.layout.SidePanel
import team.momentlog.app.ui.screens.widget.record.GraphMarker
import team.momentlog.app.ui.screens.widget.record.StepTimeline
import team.momentlog.app.ui.screens.widget.record.TimelineGraph
import team.momentlog.app.ui.screens.widget.record.TimelineStep
import team.momentlog.core.TurnSignal

/**
 * 결과 화면. 차 밖의 상대 운전자나 경찰도 읽는 화면이라 한 줄 경위를 가장 크게 둔다.
 *
 * TODO(앱): 3분 넘는 기록은 그래프 위 범위 버튼(전체 / 급제동 전후 / "기록해" 직전)으로 확대.
 *  진술에서 나온 사건(시각이 있는 것)을 단계와 그래프에 회색 점선으로 넣기.
 */
@Composable
fun RecordDetailScreen(
    recordId: String,
    recordViewModel: RecordViewModel,
    onSendToPhone: () -> Unit,
    onOpenRecords: () -> Unit,
    onStatement: () -> Unit,
) {
    LaunchedEffect(recordId) { recordViewModel.loadRecord(recordId) }
    val record by recordViewModel.selected.collectAsState()
    val current = record?.takeIf { it.id == recordId }

    AppScreen(drivingAllowed = false) {
        if (current == null) {
            Text("기록을 불러오고 있어요", style = textStyle(30, 42, color = LocalAppColors.current.textSecondary), modifier = Modifier.align(Alignment.Center))
            return@AppScreen
        }
        if (isSplit) ResultSplit(current, onSendToPhone, onStatement) else ResultFull(current, onSendToPhone, onOpenRecords, onStatement)
    }
}

@Composable
private fun BoxScope.ResultFull(record: RecordInfo, onSendToPhone: () -> Unit, onOpenRecords: () -> Unit, onStatement: () -> Unit) {
    val colors = LocalAppColors.current
    val tone = record.kind.tone(colors)

    SidePanel(540) {
        Text("0초 · ${momentTitle(record.kind)}", style = textStyle(22, 28, FontWeight.Bold, colors.textSecondary), modifier = Modifier.offset(48.q, 136.q))
        MomentCar(record, Modifier.offset(160.q, 206.q).size(220.q, 440.q))
        MomentNumbers(record, Modifier.align(Alignment.BottomStart).padding(start = 48.q, bottom = 52.q))
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 580.q, end = 56.q, top = 44.q),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(chipText(record), tone, icon = record.kind.icon)
        Spacer(Modifier.weight(1f))
        CustomButton("휴대폰으로 받기", onSendToPhone, primary = true, large = false, icon = AppIcon.QR, compact = true)
        Spacer(Modifier.width(16.q))
        CustomButton("기록 목록", onOpenRecords, large = false, icon = AppIcon.LIST, compact = true)
    }

    Column(Modifier.padding(start = 580.q, end = 56.q, top = 164.q)) {
        Text(headline(record, colors), style = textStyle(52, 68, FontWeight.ExtraBold), maxLines = 2)
        Spacer(Modifier.height(16.q))
        Text(subline(record), style = textStyle(28, 38, color = colors.textSecondary), maxLines = 1)
    }

    StepTimeline(steps(record, colors), Modifier.padding(start = 580.q, end = 56.q, top = 400.q))

    TimelineGraph(
        record.snapshots, record.triggeredAtMillis, markers(record, colors),
        Modifier.offset(580.q, 600.q).size(1014.q, 330.q),
    )

    Row(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 580.q, end = 56.q, bottom = 48.q),
        horizontalArrangement = Arrangement.spacedBy(40.q),
    ) {
        Column(Modifier.weight(1f)) {
            Text("시각", style = textStyle(20, 26, color = colors.textTertiary))
            Text(Formats.clock(record.triggeredAtMillis), style = textStyle(48, 56, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
            Text(Formats.date(record.triggeredAtMillis), style = textStyle(22, 28, color = colors.textSecondary))
        }
        Column(Modifier.weight(1.3f)) { StatementBlock(record, 26, 38, plateHeight = 56, plateFont = 30, onStatement = onStatement) }
    }
}

@Composable
private fun ResultSplit(record: RecordInfo, onSendToPhone: () -> Unit, onStatement: () -> Unit) {
    val colors = LocalAppColors.current
    val tone = record.kind.tone(colors)
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 40.q, end = 40.q, top = 24.q), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(chipText(record), tone, icon = record.kind.icon)
            Spacer(Modifier.weight(1f))
            CustomButton("받기", onSendToPhone, primary = true, large = false, icon = AppIcon.QR, compact = true)
        }
        Column(Modifier.padding(start = 40.q, end = 40.q, top = 144.q)) {
            Text(headline(record, colors), style = textStyle(44, 58, FontWeight.ExtraBold), maxLines = 2)
        }
        MomentCar(record, Modifier.offset(70.q, 346.q).size(155.q, 311.q))
        StepTimeline(steps(record, colors), Modifier.padding(start = 320.q, end = 40.q, top = 352.q), compact = true)
        TimelineGraph(
            record.snapshots, record.triggeredAtMillis, markers(record, colors),
            Modifier.offset(40.q, 690.q).size(744.q, 250.q), compact = true,
        )
        Column(Modifier.padding(start = 40.q, end = 40.q, top = 966.q)) {
            Text(Formats.clock(record.triggeredAtMillis), style = textStyle(40, 48, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(8.q))
            StatementBlock(record, 22, 32, plateHeight = 48, plateFont = 26, onStatement = onStatement)
        }
    }
}

/** 감지 순간의 내 차. 그때 켜져 있던 램프를 그린다. */
@Composable
private fun MomentCar(record: RecordInfo, modifier: Modifier) {
    val colors = LocalAppColors.current
    val at = record.facts.atTrigger
    val lamps = CarLamps(
        turnRight = at?.turn == TurnSignal.RIGHT,
        turnLeft = at?.turn == TurnSignal.LEFT,
        brake = at?.brake == true,
        head = at?.lowBeam == true || at?.highBeam == true,
    )
    Box(modifier) {
        CarDrawing(Modifier.fillMaxSize(), lamps)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 200f
            val glow = 6 * k
            listOfNotNull(
                if (lamps.turnRight) listOf(Offset(176f, 44f), Offset(184f, 60f), Offset(178f, 352f), Offset(184f, 366f)) else null,
                if (lamps.turnLeft) listOf(Offset(24f, 44f), Offset(16f, 60f), Offset(22f, 352f), Offset(16f, 366f)) else null,
            ).flatten().forEach { p ->
                val dir = if (p.x > 100) 1 else -1
                drawLine(colors.dataTurn, Offset(p.x * k, p.y * k), Offset((p.x + dir * 12) * k, (p.y - 6) * k), glow, StrokeCap.Round)
            }
            if (lamps.brake) {
                listOf(70f, 100f, 130f).forEach { x ->
                    drawLine(colors.dataBrake, Offset(x * k, 392f * k), Offset(x * k, 408f * k), 3 * k, StrokeCap.Round)
                }
            }
            if (record.kind == RecordKind.IMPACT) {
                drawArc(
                    colors.danger, 20f, 140f, false, Offset(10f * k, 360f * k), androidx.compose.ui.geometry.Size(180f * k, 60f * k),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(10 * k, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun MomentNumbers(record: RecordInfo, modifier: Modifier) {
    val colors = LocalAppColors.current
    val at = record.facts.atTrigger
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(at?.speedKph?.roundToInt()?.toString() ?: "–", style = textStyle(72, 80, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(8.q))
            Text("km/h", style = textStyle(30, 48, color = colors.textSecondary))
        }
        Spacer(Modifier.height(12.q))
        val turn = when (at?.turn) {
            TurnSignal.RIGHT -> "우측 방향지시등 켜짐" to colors.dataTurn
            TurnSignal.LEFT -> "좌측 방향지시등 켜짐" to colors.dataTurn
            TurnSignal.OFF -> "방향지시등 꺼짐" to colors.textTertiary
            null -> "방향지시등 정보 없음" to colors.textTertiary
        }
        val brake = when (at?.brake) {
            true -> "브레이크 밟는 중" to colors.dataBrake
            false -> "브레이크 안 밟음" to colors.textTertiary
            null -> "브레이크 정보 없음" to colors.textTertiary
        }
        val steer = "핸들 ${steeringText(at?.steeringDeg)}" to colors.textTertiary
        listOf(turn, brake, steer).forEach { (text, dot) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.q)) {
                Box(Modifier.size(12.q).background(dot, CircleShape))
                Spacer(Modifier.width(10.q))
                Text(text, style = textStyle(24, 32))
            }
        }
    }
}

@Composable
private fun StatementBlock(record: RecordInfo, size: Int, lineHeight: Int, plateHeight: Int, plateFont: Int, onStatement: () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("진술 · 운전자", style = textStyle(20, 26, FontWeight.Bold, colors.textTertiary))
        if (record.needsStatement) {
            Spacer(Modifier.width(20.q))
            Text(
                "진술 남기기",
                style = textStyle(24, 32, FontWeight.Bold, colors.accent),
                modifier = Modifier.clickable(onClick = onStatement).padding(8.q),
            )
        }
    }
    val statement = record.statement?.takeIf { it.isNotBlank() }
    Text(
        statement?.let { "“$it”" } ?: "아직 진술을 남기지 않았어요",
        style = textStyle(size, lineHeight, color = if (statement == null) colors.textTertiary else colors.textPrimary),
        maxLines = 2,
    )
    record.plate?.let { plate ->
        Spacer(Modifier.height(12.q))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateBadge(plate, plateHeight, plateFont)
            Spacer(Modifier.width(14.q))
            Text("상대 차량 번호 (진술)", style = textStyle(20, 26, color = colors.textTertiary))
        }
    }
}

private fun momentTitle(kind: RecordKind) = when (kind) {
    RecordKind.IMPACT -> "충격 순간의 내 차"
    RecordKind.HARD_STOP -> "급정지 순간의 내 차"
    RecordKind.VOICE, RecordKind.AFTER_STOP -> "기록 요청 순간의 내 차"
}

private fun chipText(record: RecordInfo) = when (record.kind) {
    RecordKind.IMPACT -> "충격 감지 · 자동 저장"
    RecordKind.HARD_STOP -> "급정지 · 자동 저장"
    RecordKind.VOICE -> if (record.merged) "“기록해” · 앞선 기록 합침" else "“기록해”"
    RecordKind.AFTER_STOP -> "정차 중 저장"
}


/**
 * 한 줄 경위. 가장 앞선 조작(방향지시등 → 브레이크 → 속도) 하나만 고른다.
 */
private fun headline(record: RecordInfo, colors: MomentlogColors): AnnotatedString {
    val f = record.facts
    val anchor = record.kind.anchor
    val tone = record.kind.tone(colors)
    return buildAnnotatedString {
        fun strong(text: String) = withStyle(SpanStyle(color = tone)) { append(text) }
        when {
            f.turnSecondsBefore != null -> {
                append("$anchor "); strong("${secondsText(f.turnSecondsBefore)} 전"); append("부터\n")
                append("${turnSideText(f.turn)} 방향지시등이 켜져 있었어요")
            }
            f.brakeSecondsBefore != null -> {
                append("$anchor "); strong("${secondsText(f.brakeSecondsBefore)} 전"); append("부터\n")
                append("브레이크를 밟고 있었어요")
            }
            f.speedAtTrigger != null -> {
                append("$anchor 때 "); strong("${f.speedAtTrigger.roundToInt()}km/h"); append("로\n")
                append("달리고 있었어요")
            }
            else -> append("$anchor 순간의 기록을\n저장했어요")
        }
    }
}

private fun subline(record: RecordInfo): String {
    val f = record.facts
    return listOfNotNull(
        f.brakeSecondsBefore?.takeIf { f.turnSecondsBefore != null }?.let { "${secondsText(it)} 전부터 브레이크" },
        f.speedAtTrigger?.let { "${record.kind.anchor} 때 ${it.roundToInt()}km/h" },
        "핸들 ${steeringText(f.steeringAtTrigger)}",
    ).joinToString(" · ")
}

private fun steps(record: RecordInfo, colors: MomentlogColors): List<TimelineStep> {
    val f = record.facts
    val tone: Color = record.kind.tone(colors)
    return listOfNotNull(
        f.turnSecondsBefore?.let { TimelineStep(-it, "${turnSideText(f.turn)} 방향지시등 켬", colors.dataTurn) },
        f.brakeSecondsBefore?.let { s ->
            TimelineStep(-s, "브레이크 시작" + (f.brakeStartSpeed?.let { " · ${it.roundToInt()}km/h" } ?: ""), colors.dataBrake)
        },
        TimelineStep(0f, record.kind.label + (f.speedAtTrigger?.let { " · ${it.roundToInt()}km/h" } ?: ""), tone),
    ).plus(
        record.otherEvents.map { e ->
            val kind = RecordKind.of(e)
            TimelineStep((e.atMillis - record.triggeredAtMillis) / 1000f, kind.label, kind.tone(colors))
        },
    ).sortedBy { it.seconds }
}

private fun markers(record: RecordInfo, colors: MomentlogColors): List<GraphMarker> =
    record.events.map { e ->
        val kind = if (e.atMillis == record.triggeredAtMillis && e.trigger == record.trigger) record.kind else RecordKind.of(e)
        GraphMarker((e.atMillis - record.triggeredAtMillis) / 1000f, null, kind.tone(colors))
    }
