package team.momentlog.app.ui.screens.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color2F6FDE
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.colorE5E5E5
import team.momentlog.app.config.theme.colorEAF1FF
import team.momentlog.app.config.theme.colorF5F5F5
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.widget.record.TimelineGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 4 기록 상세. 맨 위 경위 문장이 결론, 아래 그래프가 근거. 오른쪽에 위치·환경·진술.
 * TODO(앱): 위치 지도, 전조등·와이퍼·에어백 칩, 진술 내용 표시
 */
@Composable
fun RecordDetailScreen(
    recordId: String,
    recordViewModel: RecordViewModel,
    onMakeDocument: () -> Unit,
    onStatement: () -> Unit,
) {
    LaunchedEffect(recordId) { recordViewModel.loadRecord(recordId) }
    val record by recordViewModel.selected.collectAsState()
    val timeFormat = remember { SimpleDateFormat("M월 d일 HH:mm:ss", Locale.KOREA) }

    val current = record?.takeIf { it.id == recordId }
    if (current == null) {
        Text("기록을 불러오고 있어요", color = color6B6B6B)
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "${timeFormat.format(Date(current.triggeredAtMillis))} · ${current.triggerLabel}",
                fontSize = 15.sp,
                color = color6B6B6B,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colorEAF1FF, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, color2F6FDE), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                current.narrative.forEachIndexed { index, line ->
                    Text(
                        line,
                        fontSize = 17.sp,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = color111111,
                    )
                }
            }
            TimelineGraph(current.snapshots, current.triggeredAtMillis, Modifier.fillMaxWidth())
        }
        Column(Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "위치 지도",
                color = color6B6B6B,
                modifier = Modifier.fillMaxWidth().height(140.dp).background(colorF5F5F5, RoundedCornerShape(12.dp)).padding(16.dp),
            )
            Column(
                Modifier.fillMaxWidth().border(BorderStroke(1.dp, colorE5E5E5), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("진술", fontSize = 13.sp, color = color6B6B6B)
                Text("아직 진술이 없어요", fontSize = 16.sp, color = color111111)
            }
            CustomButton("진술하기", onClick = onStatement, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            CustomButton("사고 정리 문서 만들기", onClick = onMakeDocument, modifier = Modifier.fillMaxWidth(), primary = true)
        }
    }
}
