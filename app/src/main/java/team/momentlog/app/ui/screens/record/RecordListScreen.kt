package team.momentlog.app.ui.screens.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.Constant
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color2F6FDE
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.colorD93B3B
import team.momentlog.app.config.theme.colorE6F6EC
import team.momentlog.app.config.theme.colorEAF1FF
import team.momentlog.app.config.theme.colorFDECEC
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.core.Trigger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 6 기록 목록. 행을 누르면 기록 상세로 간다. */
@Composable
fun RecordListScreen(recordViewModel: RecordViewModel, onOpen: (RecordInfo) -> Unit) {
    LaunchedEffect(Unit) { recordViewModel.loadRecords() }
    val records by recordViewModel.records.collectAsState()
    val timeFormat = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("기록 ${records.size}건", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = color111111)
            Text(
                "보관하지 않은 기록은 ${Constant.RETENTION_DAYS}일 뒤 삭제돼요",
                fontSize = 14.sp,
                color = color6B6B6B,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
        }
        HorizontalDivider()
        if (records.isEmpty()) {
            Text("아직 저장된 기록이 없어요. 급정지가 감지되거나 “기록해”라고 말하면 여기에 쌓여요.", fontSize = 16.sp, color = color6B6B6B)
            return@Column
        }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                RecordRow(record, timeFormat.format(Date(record.triggeredAtMillis)), onClick = { onOpen(record) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RecordRow(record: RecordInfo, time: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val (badgeColor, badgeBackground) = when (record.trigger) {
            Trigger.VOICE -> color2F6FDE to colorEAF1FF
            Trigger.HARD_STOP, Trigger.AIRBAG -> colorD93B3B to colorFDECEC
        }
        Text(
            record.triggerLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color111111,
            modifier = Modifier
                .background(badgeBackground, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, badgeColor), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(time, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color111111)
            Text(record.narrative.firstOrNull().orEmpty(), fontSize = 14.sp, color = color6B6B6B, maxLines = 1)
        }
        Text(
            if (record.sealed) "원본 봉인됨" else "봉인 대기",
            fontSize = 14.sp,
            color = color111111,
            modifier = Modifier
                .background(if (record.sealed) colorE6F6EC else colorEAF1FF, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
