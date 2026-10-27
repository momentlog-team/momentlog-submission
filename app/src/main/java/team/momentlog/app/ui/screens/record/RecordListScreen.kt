package team.momentlog.app.ui.screens.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.record.RecordStore
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.models.Formats
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.app.ui.models.RecordKind
import team.momentlog.app.ui.models.secondsText
import team.momentlog.app.ui.models.steeringText
import team.momentlog.app.ui.models.turnSideText
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.widget.car.CarDrawing
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon
import team.momentlog.app.ui.screens.widget.label.InfoNote
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.layout.SidePanel
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow

private const val BAR_FULL_SECONDS = 210f
private const val BAR_FULL_WIDTH = 360

/**
 * 기록 목록. 세로 분할 창에서는 미리보기 없이 행을 누르면 결과 화면으로 간다.
 */
@Composable
fun RecordListScreen(
    recordViewModel: RecordViewModel,
    onOpen: (RecordInfo) -> Unit,
    onStatement: (RecordInfo) -> Unit,
    onSendToPhone: (RecordInfo) -> Unit,
    onSettings: () -> Unit,
) {
    LaunchedEffect(Unit) { recordViewModel.loadRecords() }
    val records by recordViewModel.records.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = records.firstOrNull { it.id == selectedId } ?: records.firstOrNull()
    val split = isSplit
    val colors = LocalAppColors.current
    val now = System.currentTimeMillis()

    AppScreen(drivingAllowed = false) {
        Column(
            Modifier
                .then(if (split) Modifier.fillMaxWidth() else Modifier.width(1000.q))
                .fillMaxHeight()
                .padding(start = 56.q, top = 44.q, end = 40.q, bottom = 40.q),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append("내 기록 ")
                        withStyle(SpanStyle(color = colors.accent)) { append(records.size.toString()) }
                    },
                    style = textStyle(40, 48, FontWeight.ExtraBold),
                )
                Spacer(Modifier.weight(1f))
                if (!split) Text("시동을 꺼도 저장된 기록은 남아요", style = textStyle(22, 28, color = colors.textSecondary))
                Spacer(Modifier.width(16.q))
                Text(
                    "설정", style = textStyle(22, 28, FontWeight.Bold, colors.textTertiary),
                    modifier = Modifier.clickable(onClick = onSettings).padding(8.q),
                )
            }
            Spacer(Modifier.height(32.q))
            if (records.isEmpty()) {
                EmptyRecords(Modifier.fillMaxSize())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.q)) {
                    itemsIndexed(records, key = { _, r -> r.id }) { index, record ->
                        RecordRow(
                            record, now,
                            selected = !split && record.id == selected?.id,
                            stackChips = split,
                            onClick = { if (split) onOpen(record) else selectedId = record.id },
                            modifier = Modifier.enterFromBelow(12.q, delayMillis = index * 50),
                        )
                    }
                }
            }
        }

        if (!split && selected != null) {
            SidePanel(650, Modifier.align(Alignment.TopEnd)) {
                Preview(
                    selected, now,
                    onOpen = { onOpen(selected) },
                    onStatement = { onStatement(selected) },
                    onRetain = { recordViewModel.retain(selected.id) },
                    onSendToPhone = { onSendToPhone(selected) },
                )
            }
        }
    }
}

@Composable
private fun RecordRow(record: RecordInfo, now: Long, selected: Boolean, stackChips: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    val tone = record.kind.tone(colors)
    val shape = RoundedCornerShape(24.q)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.shadow(4.q, shape, ambientColor = Color(0x1410151B), spotColor = Color(0x1410151B)).background(colors.surface, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.q, vertical = 24.q),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.q).background(tone.copy(alpha = 0.14f), RoundedCornerShape(20.q)), contentAlignment = Alignment.Center) {
            Icon(record.kind.icon, 36.q, tone)
        }
        Spacer(Modifier.width(24.q))
        Column(Modifier.weight(1f)) {
            Text(listTitle(record), style = textStyle(28, 38, FontWeight.Bold), maxLines = 1)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${Formats.shortDate(record.triggeredAtMillis)} ${Formats.clock(record.triggeredAtMillis)}")
                    }
                    append(" · ${record.kind.label}")
                },
                style = textStyle(22, 30, color = colors.textSecondary),
            )
            Spacer(Modifier.height(10.q))
            LengthBar(record, tone)
            if (stackChips) {
                Spacer(Modifier.height(12.q))
                StateChips(record, now)
            }
        }
        if (!stackChips) {
            Spacer(Modifier.width(16.q))
            StateChips(record, now)
        }
    }
}

/** 기록 길이를 폭으로, 감지 시점을 점으로. */
@Composable
private fun LengthBar(record: RecordInfo, tone: Color) {
    val colors = LocalAppColors.current
    val seconds = record.durationSeconds.coerceAtLeast(1)
    val width = (seconds / BAR_FULL_SECONDS * BAR_FULL_WIDTH).coerceIn(24f, BAR_FULL_WIDTH.toFloat())
    val span = (record.endMillis - record.startMillis).coerceAtLeast(1).toFloat()
    val dots = record.events.map { e -> ((e.atMillis - record.startMillis) / span).coerceIn(0f, 1f) to RecordKind.of(e).tone(colors) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val unit = 1.q
        Canvas(Modifier.width(width.q).height(18.q)) {
            val u = unit.toPx()
            drawRoundRect(tone.copy(alpha = 0.3f), Offset(0f, size.height / 2 - 5 * u), Size(size.width, 10 * u), CornerRadius(5 * u))
            dots.forEach { (ratio, color) ->
                val cx = (size.width * ratio).coerceIn(9 * u, size.width - 9 * u)
                drawCircle(colors.surface, 9 * u, Offset(cx, size.height / 2))
                drawCircle(color, 6 * u, Offset(cx, size.height / 2))
            }
        }
        Spacer(Modifier.width(12.q))
        Text(lengthText(seconds), style = textStyle(20, 26, color = colors.textSecondary))
    }
}

@Composable
private fun StateChips(record: RecordInfo, now: Long) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.q)) {
        if (record.statementState == RecordStore.StatementState.LATER || record.statementState == RecordStore.StatementState.WRITING) {
            SmallChip(if (record.statementState == RecordStore.StatementState.WRITING) "진술 작성 중" else "진술 미작성", colors.warning, colors.warning.copy(alpha = 0.16f))
        }
        val days = record.daysUntilDelete(now)
        if (days == null) {
            SmallChip("보관됨", colors.textSecondary, colors.surfaceSunk, AppIcon.LOCK)
        } else {
            SmallChip("${days}일 뒤 삭제", colors.textSecondary, Color.Transparent, AppIcon.CLOCK, dashed = true)
        }
    }
}

@Composable
private fun SmallChip(text: String, color: Color, background: Color, icon: AppIcon? = null, dashed: Boolean = false) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .height(44.q)
            .background(background, CircleShape)
            .then(if (dashed) Modifier.border(2.q, colors.line, CircleShape) else Modifier)
            .padding(horizontal = 18.q),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, 22.q, color)
            Spacer(Modifier.width(8.q))
        }
        Text(text, style = textStyle(20, 24, FontWeight.Bold, color), maxLines = 1)
    }
}

@Composable
private fun Preview(record: RecordInfo, now: Long, onOpen: () -> Unit, onStatement: () -> Unit, onRetain: () -> Unit, onSendToPhone: () -> Unit) {
    val colors = LocalAppColors.current
    val tone = record.kind.tone(colors)
    Column(Modifier.fillMaxSize().padding(56.q).enterFromBelow(16.q, key = record.id)) {
        StatusChip(record.kind.label, tone, icon = record.kind.icon)
        Spacer(Modifier.height(32.q))
        Text(listTitle(record), style = textStyle(40, 52, FontWeight.ExtraBold))
        Spacer(Modifier.height(24.q))
        Text(Formats.clock(record.triggeredAtMillis), style = textStyle(48, 56, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
        Text(Formats.date(record.triggeredAtMillis), style = textStyle(24, 32, color = colors.textSecondary))
        Spacer(Modifier.height(24.q))
        val days = record.daysUntilDelete(now)
        InfoNote(
            if (days == null) "충격이 감지됐거나 진술을 남긴 기록은 자동으로 보관해요" else "보관하지 않으면 ${days}일 뒤 지워져요",
            size = 22,
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(16.q)) {
            val full = Modifier.fillMaxWidth()
            when {
                record.needsStatement && record.statementState != RecordStore.StatementState.NONE -> {
                    CustomButton("진술 추가", onStatement, full, primary = true, large = false, icon = AppIcon.MIC)
                    CustomButton("결과 보기", onOpen, full, large = false)
                }
                days != null -> {
                    CustomButton("보관하기", onRetain, full, primary = true, large = false, icon = AppIcon.LOCK)
                    CustomButton("결과 보기", onOpen, full, large = false)
                }
                else -> {
                    CustomButton("결과 보기", onOpen, full, primary = true, large = false)
                    CustomButton("휴대폰으로 받기", onSendToPhone, full, large = false, icon = AppIcon.QR)
                }
            }
        }
    }
}

@Composable
private fun EmptyRecords(modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CarDrawing(Modifier.size(140.q, 280.q))
        Spacer(Modifier.height(32.q))
        Text("아직 저장된 기록이 없어요", style = textStyle(40, 52, FontWeight.ExtraBold))
        Spacer(Modifier.height(16.q))
        Text(
            "최근 3분은 계속 지키고 있어요. 사고나 급정지가 감지되거나 “기록해”라고 하면 여기에 남아요.",
            style = textStyle(30, 42, color = colors.textSecondary),
            textAlign = TextAlign.Center,
        )
    }
}

private fun listTitle(record: RecordInfo): String {
    val f = record.facts
    val anchor = record.kind.anchor
    return when {
        f.turnSecondsBefore != null -> "$anchor ${secondsText(f.turnSecondsBefore)} 전부터 ${turnSideText(f.turn)} 방향지시등 켜짐"
        f.brakeSecondsBefore != null && f.brakeStartSpeed != null && f.speedAtTrigger != null ->
            "$anchor ${secondsText(f.brakeSecondsBefore)} 전, ${f.brakeStartSpeed.roundToInt()}→${f.speedAtTrigger.roundToInt()}km/h 급제동"
        f.speedAtTrigger != null -> "$anchor 때 ${f.speedAtTrigger.roundToInt()}km/h, 핸들 ${steeringText(f.steeringAtTrigger)}"
        record.kind == RecordKind.HARD_STOP -> "급정지만 감지됐어요"
        else -> "${record.kind.label} 기록"
    }
}

private fun lengthText(seconds: Int) = if (seconds >= 60) "${seconds / 60}분 ${seconds % 60}초" else "${seconds}초"
