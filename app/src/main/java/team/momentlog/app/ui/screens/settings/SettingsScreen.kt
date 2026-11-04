package team.momentlog.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.record.SyncStatusCard
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.record.SyncStatus

/**
 * 설정. 지금은 개발·시연용 기록 제어만 둔다.
 * TODO(앱): 감지 민감도, 보관 기간, 정차 후 질문 켜기/끄기, 신호별 지원 여부(check*Capabilities 결과)
 */
@Composable
fun SettingsScreen(mainViewModel: MainViewModel, onBack: () -> Unit) {
    val syncStatus by mainViewModel.syncStatus.collectAsState()
    SettingsContent(syncStatus, onBack, mainViewModel::retrySync,
        { mainViewModel.startRecording(replay = false) },
        { mainViewModel.startRecording(replay = true) }, mainViewModel::stopRecording)
}

@Composable
internal fun SettingsContent(syncStatus: SyncStatus, onBack: () -> Unit, onRetry: () -> Unit,
                             onStart: () -> Unit, onReplay: () -> Unit, onStop: () -> Unit) {
    AppScreen(drivingAllowed = false) {
        Column(Modifier.fillMaxSize().padding(56.q), verticalArrangement = Arrangement.spacedBy(32.q)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CustomButton("뒤로", onClick = onBack, large = false, icon = AppIcon.BACK)
                Spacer(Modifier.width(24.q))
                Text("설정", style = textStyle(40, 48, FontWeight.ExtraBold))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.q)) {
                SyncStatusCard(syncStatus, onRetry)
                Text("기록 제어 (개발·시연용)", style = textStyle(30, 42, color = LocalAppColors.current.textSecondary))
                if (isSplit) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.q)) {
                    CustomButton("기록 시작", onStart, Modifier.fillMaxWidth(), primary = true)
                    CustomButton("시연 재생", onReplay, Modifier.fillMaxWidth())
                    CustomButton("중지", onStop, Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.q)) {
                    CustomButton("기록 시작", onClick = onStart, primary = true)
                    CustomButton("시연 재생", onClick = onReplay)
                    CustomButton("중지", onClick = onStop)
                }
            }
        }
    }
}
