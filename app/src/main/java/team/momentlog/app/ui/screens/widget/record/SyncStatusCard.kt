package team.momentlog.app.ui.screens.widget.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.record.SyncStatus
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.widget.label.StatusChip

@Composable
fun SyncStatusCard(status: SyncStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(24.q)
    Column(modifier.fillMaxWidth().background(colors.surface, shape).border(2.q, colors.line, shape).padding(32.q),
        verticalArrangement = Arrangement.spacedBy(20.q)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("서버 전송", style = textStyle(32, 42, FontWeight.Bold))
            StatusChip(when {
                status.checking || !status.checked -> "확인 중"
                status.needsAttention -> "확인 필요"
                status.pending > 0 -> "대기"
                else -> "완료"
            }, if (status.needsAttention) colors.warning else colors.textSecondary)
        }
        if (!status.checking && !status.needsAttention) Text(status.title, style = textStyle(26, 36, color = colors.textSecondary))
        if (status.pending > 0) {
            Text("봉인 대기 ${status.pendingSeals}건 · 공유 철회 대기 ${status.pendingRevocations}건", style = textStyle(24, 34, color = colors.textSecondary))
            Text(if (status.offline) "연결이 돌아오면 다시 보내요." else "잠시 후 자동으로 다시 보내요.", style = textStyle(24, 34, color = colors.textSecondary))
            if (status.pendingRevocations > 0) Text("철회가 끝나기 전까지 공유 링크가 열릴 수 있어요.", style = textStyle(24, 34, color = colors.warning))
        }
        if (status.sealFailures.isNotEmpty()) {
            Text("봉인 중단 ${status.sealFailures.size}건", style = textStyle(26, 36, FontWeight.Bold, colors.warning))
            Text(status.failureMessage(status.sealFailures), style = textStyle(24, 34, color = colors.textSecondary))
            Text("기록은 차 안에 남아 있어요.", style = textStyle(24, 34, color = colors.textSecondary))
        }
        if (status.revocationFailures.isNotEmpty()) {
            Text("공유 철회 중단 ${status.revocationFailures.size}건", style = textStyle(26, 36, FontWeight.Bold, colors.warning))
            Text(status.failureMessage(status.revocationFailures), style = textStyle(24, 34, color = colors.textSecondary))
            Text("공유 링크가 아직 열릴 수 있어요.", style = textStyle(24, 34, color = colors.warning))
        }
        if (status.localError) Text("기기에 저장된 전송 상태를 읽지 못했어요. 다시 확인해 주세요.", style = textStyle(24, 34, color = colors.warning))
        if (status.needsAttention || status.pending > 0) {
            Spacer(Modifier.height(4.q))
            CustomButton(if (status.checking) "확인 중" else "다시 확인", onRetry, large = false, enabled = !status.checking)
            if (status.needsAttention) Text("계속 중단되면 지원 담당자에게 문의해 주세요.", style = textStyle(22, 32, color = colors.textSecondary))
        }
    }
}
