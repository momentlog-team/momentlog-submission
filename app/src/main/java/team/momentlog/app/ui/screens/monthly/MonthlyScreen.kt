package team.momentlog.app.ui.screens.monthly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import java.util.Calendar
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.widget.car.CarDrawing
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.layout.SidePanel
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow

/**
 * 월간 점검 안내와 구독 안내.
 *
 * TODO(앱): "서버 전송 정상"은 봉인 결과를 모아서 계산, 신호 이상은 한 달 동안 날짜별 수신 여부를 남겨서 계산.
 */
@Composable
fun MonthlyScreen(
    subscribe: Boolean,
    mainViewModel: MainViewModel,
    recordViewModel: RecordViewModel,
    onConfirm: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    LaunchedEffect(Unit) { recordViewModel.loadRecords() }
    val allRecords by recordViewModel.records.collectAsState()
    val records = allRecords.filterNot { it.isMock }
    val unsupported by mainViewModel.unsupportedSignals.collectAsState()
    val colors = LocalAppColors.current
    val split = isSplit
    val month = Calendar.getInstance().get(Calendar.MONTH) + 1
    val lastDay = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
    val signalIssue = !subscribe && unsupported.isNotEmpty()

    AppScreen(drivingAllowed = false) {
        if (!split) {
            SidePanel(700) {
                Text(
                    "${month}월 점검 · %02d.01 ~ %02d.%02d".format(month, month, lastDay),
                    style = textStyle(24, 32, FontWeight.Bold, colors.textSecondary),
                    modifier = Modifier.offset(48.q, 64.q),
                )
                CarDrawing(Modifier.offset(240.q, 230.q).size(220.q, 440.q))
                SignalBadge("방향지시등", "방향지시등" !in unsupported, left = 480.q, top = 240.q)
                SignalBadge("브레이크", "브레이크" !in unsupported, left = 480.q, top = 560.q)
                SignalBadge("조향각", "조향각" !in unsupported, rightEdge = 220.q, top = 300.q)
                SignalBadge("속도·위치", "속도" !in unsupported, rightEdge = 220.q, top = 460.q)
            }
        }

        Column(
            Modifier
                .fillMaxHeight()
                .padding(start = if (split) 48.q else 760.q, end = if (split) 48.q else 64.q, bottom = 200.q)
                .enterFromBelow(16.q),
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                subscribe -> StatusChip("문서 받기", colors.accent, icon = AppIcon.QR)
                signalIssue -> StatusChip("${month}월 점검", colors.warning, icon = AppIcon.WARN)
                else -> StatusChip("${month}월 점검", colors.recording, icon = AppIcon.CHECK)
            }
            Spacer(Modifier.height(32.q))
            Text(
                when {
                    subscribe -> "문서를 더 받으려면 구독이 필요해요"
                    signalIssue -> "${unsupported.first()} 신호를 받지 못했어요"
                    else -> "${month}월 기록 상태 정상이에요"
                },
                style = textStyle(if (split) 48 else 60, if (split) 62 else 76, FontWeight.ExtraBold),
            )
            Spacer(Modifier.height(16.q))
            Text(
                if (subscribe) "구독하지 않아도 기록 저장과 현장 결과 화면은 계속 돼요" else "기록이 제대로 남는지 한 달에 한 번 알려 드려요",
                style = textStyle(30, 42, color = colors.textSecondary),
            )
            Spacer(Modifier.height(48.q))
            if (subscribe) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.q)) {
                    PlanCard("지금 (무료)", "최근 기록 1건 문서", Modifier.weight(1f), highlighted = false)
                    PlanCard("구독", "모든 기록 문서 · 가격 미정", Modifier.weight(1f), highlighted = true)
                }
            } else {
                val kept = records.count { it.kept }
                Row(horizontalArrangement = Arrangement.spacedBy(32.q)) {
                    Stat("저장된 기록", "${records.size}건", "보관 $kept · 삭제 예정 ${records.size - kept}", Modifier.weight(1f))
                    Stat("신호 수신", "${4 - unsupported.size}/4", if (unsupported.isEmpty()) "모두 받고 있어요" else "${unsupported.joinToString("·")} 수신 안 됨", Modifier.weight(1f))
                    Stat("서버 전송", if (records.all { it.sealed }) "정상" else "대기", "원본 지문 봉인", Modifier.weight(1f))
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (split) 48.q else 760.q, end = if (split) 48.q else 64.q, bottom = 72.q),
            horizontalArrangement = Arrangement.spacedBy(16.q),
        ) {
            if (subscribe) {
                // TODO(앱): 결제 화면으로 연결
                CustomButton("구독 알아보기", onConfirm, Modifier.widthIn(min = 320.q), primary = true)
                CustomButton("나중에", onConfirm)
            } else {
                CustomButton("확인", onConfirm, Modifier.widthIn(min = 320.q), primary = true)
                CustomButton(if (signalIssue) "자세히 보기" else "기록 목록", onOpenRecords)
            }
        }
    }
}

@Composable
private fun SignalBadge(name: String, ok: Boolean, top: Dp, left: Dp? = null, rightEdge: Dp? = null) {
    val colors = LocalAppColors.current
    val width = 200.q
    val tint = if (ok) colors.recording else colors.warning
    Row(
        Modifier.offset(x = left ?: (rightEdge!! - width), y = top).width(width),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (left != null) Arrangement.Start else Arrangement.End,
    ) {
        Box(Modifier.size(36.q).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (ok) AppIcon.OK else AppIcon.WARN, 24.q, tint)
        }
        Spacer(Modifier.width(10.q))
        Column {
            Text(name, style = textStyle(24, 30, FontWeight.Bold))
            Text(if (ok) "정상" else "수신 안 됨", style = textStyle(20, 26, color = colors.textSecondary))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, note: String, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier) {
        HorizontalDivider(thickness = 2.q, color = colors.line)
        Spacer(Modifier.height(16.q))
        Text(label, style = textStyle(20, 26, color = colors.textTertiary))
        Text(value, style = textStyle(40, 52, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))
        Text(note, style = textStyle(22, 30, color = colors.textSecondary))
    }
}

@Composable
private fun PlanCard(title: String, body: String, modifier: Modifier, highlighted: Boolean) {
    val colors = LocalAppColors.current
    Column(
        modifier
            .background(if (highlighted) colors.accent.copy(alpha = 0.12f) else colors.surface, RoundedCornerShape(20.q))
            .padding(32.q),
    ) {
        Text(title, style = textStyle(24, 30, FontWeight.Bold, if (highlighted) colors.accent else colors.textSecondary))
        Spacer(Modifier.height(8.q))
        Text(body, style = textStyle(30, 40, FontWeight.Bold), textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
    }
}
