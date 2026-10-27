package team.momentlog.app.ui.screens.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color1F9D55
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.colorE5E5E5
import team.momentlog.app.config.theme.colorE6F6EC
import team.momentlog.app.ui.screens.use_case.DocumentViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel

/**
 * 5 문서 받기. 왼쪽 PDF 미리보기, 오른쪽 휴대폰으로 받는 QR과 봉인 상태.
 * TODO(앱): PDF 미리보기, QR 그리기(문서 API가 나오면)
 */
@Composable
fun DocumentScreen(recordId: String, recordViewModel: RecordViewModel, documentViewModel: DocumentViewModel) {
    val record by recordViewModel.selected.collectAsState()
    val verify by documentViewModel.verify.collectAsState()
    val failure by documentViewModel.failure.collectAsState()

    LaunchedEffect(record) {
        record?.takeIf { it.id == recordId }?.let(documentViewModel::checkSeal)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(
            Modifier.weight(1f).fillMaxHeight().border(BorderStroke(1.dp, colorE5E5E5), RoundedCornerShape(8.dp)).padding(24.dp),
        ) {
            Text("사고 정리 문서 미리보기", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color111111)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(220.dp).border(BorderStroke(1.dp, colorE5E5E5), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("QR", color = color6B6B6B) }
            Text("휴대폰 카메라로 찍어서 받기", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color111111)
            Text("블랙박스 영상과 함께 보험사·경찰에 내세요", fontSize = 15.sp, color = color6B6B6B)
            val sealText = when {
                verify != null -> "서버 봉인 확인 · ${verify?.status}"
                failure != null -> failure.toString()
                else -> "봉인 확인 중"
            }
            Text(
                sealText,
                fontSize = 15.sp,
                color = color111111,
                modifier = Modifier
                    .background(colorE6F6EC, RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, color1F9D55), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}
