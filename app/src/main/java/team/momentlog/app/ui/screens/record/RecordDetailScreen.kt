package team.momentlog.app.ui.screens.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger
import team.momentlog.app.ui.models.snapshotAt
import team.momentlog.app.ui.models.relativeText
import team.momentlog.app.ui.screens.widget.car.DrivingPlaybackCar
import team.momentlog.app.ui.screens.widget.record.SignalTelemetry
import team.momentlog.app.ui.screens.widget.icon.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role

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
        var replayKey by remember(current.id) { mutableIntStateOf(0) }
        var playing by remember(current.id) { mutableStateOf(true) }
        val progress = remember(current.id, replayKey) { Animatable(0f) }
        val start = maxOf(current.startMillis, current.triggeredAtMillis - 10_000)
        val end = maxOf(current.triggeredAtMillis, current.events.filter { it.trigger == Trigger.AIRBAG }.maxOfOrNull { it.atMillis } ?: current.triggeredAtMillis).coerceAtMost(current.endMillis)
        LaunchedEffect(current.id, replayKey, playing) {
            if (playing) progress.animateTo(1f, tween((6000 * (1 - progress.value)).toInt().coerceAtLeast(1), if (progress.value == 0f) 450 else 0, LinearEasing))
        }
        val playhead = start + ((end - start) * progress.value).toLong()
        val snapshot = current.snapshotAt(playhead)
        val finished = progress.value >= 1f
        val replay: () -> Unit = {
            if (finished) { replayKey++; playing = true } else playing = !playing
        }
        if (isSplit) ResultSplit(current, onSendToPhone, onStatement, snapshot, playhead, replay, playing, finished)
        else ResultFull(current, onSendToPhone, onOpenRecords, onStatement, snapshot, playhead, replay, playing, finished)
    }
}

@Composable
private fun BoxScope.ResultFull(record: RecordInfo, onSendToPhone: () -> Unit, onOpenRecords: () -> Unit, onStatement: () -> Unit, snapshot: Snapshot?, playhead: Long, onReplay: () -> Unit, playing: Boolean, finished: Boolean) {
    val colors = LocalAppColors.current
    val tone = record.kind.tone(colors)

    SidePanel(540) {
        Row(Modifier.fillMaxWidth().padding(start = 48.q, top = 100.q, end = 48.q), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(relativeText((playhead - record.triggeredAtMillis) / 1000f), style = textStyle(32, 40, FontWeight.Bold))
                Text(if (finished) "기록 재생 완료" else if (playing) "차량 신호 재생" else "일시정지", style = textStyle(20, 28, color = colors.textSecondary))
            }
            PlaybackButton(playing, finished, onReplay)
        }
        MomentCar(record, snapshot, playhead, Modifier.offset(160.q, 230.q).size(220.q, 440.q))
        SignalTelemetry(snapshot, Modifier.align(Alignment.BottomStart).padding(start = 48.q, bottom = 52.q).width(444.q))
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
        animate = false, playheadMillis = playhead,
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
private fun ResultSplit(record: RecordInfo, onSendToPhone: () -> Unit, onStatement: () -> Unit, snapshot: Snapshot?, playhead: Long, onReplay: () -> Unit, playing: Boolean, finished: Boolean) {
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
        MomentCar(record, snapshot, playhead, Modifier.offset(70.q, 346.q).size(155.q, 311.q))
        Row(Modifier.offset(40.q, 280.q).width(744.q), verticalAlignment = Alignment.CenterVertically) {
            Text(relativeText((playhead - record.triggeredAtMillis) / 1000f), style = textStyle(24, 32, FontWeight.Bold), modifier = Modifier.weight(1f))
            PlaybackButton(playing, finished, onReplay)
        }
        SignalTelemetry(snapshot, Modifier.offset(320.q, 346.q).width(464.q))
        TimelineGraph(
            record.snapshots, record.triggeredAtMillis, markers(record, colors),
            Modifier.offset(40.q, 690.q).size(744.q, 250.q), compact = true,
            animate = false, playheadMillis = playhead,
        )
        Column(Modifier.padding(start = 40.q, end = 40.q, top = 966.q)) {
            Text(Formats.clock(record.triggeredAtMillis), style = textStyle(40, 48, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(8.q))
            StatementBlock(record, 22, 32, plateHeight = 48, plateFont = 26, onStatement = onStatement)
        }
    }
}

@Composable
private fun PlaybackButton(playing: Boolean, finished: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val label = if (finished) "처음부터 다시 재생" else if (playing) "일시정지" else "이어서 재생"
    Box(
        Modifier.size(64.q).background(colors.surface, RoundedCornerShape(20.q))
            .semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (finished) AppIcon.REDO else if (playing) AppIcon.PAUSE else AppIcon.PLAY, 30.q, colors.textPrimary)
    }
}

/** 기록 시각에 맞춰 도로·차체·램프를 재생한다. */
@Composable
private fun MomentCar(record: RecordInfo, at: Snapshot?, playhead: Long, modifier: Modifier) {
    DrivingPlaybackCar(record, at, playhead, modifier)
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
        GraphMarker((e.atMillis - record.triggeredAtMillis) / 1000f, if (e.trigger == Trigger.AIRBAG) "충격 감지" else null, kind.tone(colors))
    }
