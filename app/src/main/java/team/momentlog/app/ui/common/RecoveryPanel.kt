package team.momentlog.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle

/** 조회 실패·기록 없음·로딩 중에도 사용자가 현재 화면을 벗어날 수 있게 한다. */
@Composable
fun RecoveryPanel(message: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.padding(40.q), verticalArrangement = Arrangement.spacedBy(24.q)) {
        Text(message, style = textStyle(30, 42))
        onRetry?.let { CustomButton("다시 시도", it, Modifier.fillMaxWidth()) }
        CustomButton(backLabel, onBack, Modifier.fillMaxWidth())
    }
}
