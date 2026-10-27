package team.momentlog.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.widget.layout.AppScreen

/**
 * 설정. 지금은 개발·시연용 기록 제어만 둔다.
 * TODO(앱): 감지 민감도, 보관 기간, 정차 후 질문 켜기/끄기, 신호별 지원 여부(check*Capabilities 결과)
 */
@Composable
fun SettingsScreen(mainViewModel: MainViewModel) {
    AppScreen(drivingAllowed = false) {
        Column(Modifier.padding(56.q), verticalArrangement = Arrangement.spacedBy(32.q)) {
            Text("설정", style = textStyle(40, 48, FontWeight.ExtraBold))
            Text("기록 제어 (개발·시연용)", style = textStyle(30, 42, color = LocalAppColors.current.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(16.q)) {
                CustomButton("기록 시작", onClick = { mainViewModel.startRecording(replay = false) }, primary = true)
                CustomButton("시연 재생", onClick = { mainViewModel.startRecording(replay = true) })
                CustomButton("중지", onClick = mainViewModel::stopRecording)
            }
        }
    }
}
