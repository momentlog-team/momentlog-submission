package team.momentlog.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.MainViewModel

/**
 * 설정. 지금은 개발·시연용 기록 제어만 둔다.
 * TODO(앱): 감지 민감도, 보관 기간, 정차 후 질문 켜기/끄기, 신호별 지원 여부(check*Capabilities 결과)
 */
@Composable
fun SettingsScreen(mainViewModel: MainViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("설정", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = color111111)
        HorizontalDivider()
        Text("기록 제어 (개발·시연용)", fontSize = 16.sp, color = color6B6B6B)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CustomButton("기록 시작", onClick = { mainViewModel.startRecording(replay = false) }, primary = true)
            CustomButton("시연 재생", onClick = { mainViewModel.startRecording(replay = true) })
            CustomButton("중지", onClick = mainViewModel::stopRecording)
        }
    }
}
