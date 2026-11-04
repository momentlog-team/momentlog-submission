package team.momentlog.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.config.theme.LocalAppColors

/** 조회 실패·기록 없음·로딩 중에도 사용자가 현재 화면을 벗어날 수 있게 한다. */
@Composable
fun RecoveryPanel(message: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(24.q)
    Column(modifier.widthIn(max = 820.q).fillMaxWidth().background(colors.surface, shape)
        .border(2.q, colors.line, shape).verticalScroll(rememberScrollState()).padding(40.q), verticalArrangement = Arrangement.spacedBy(24.q)) {
        Text(message, style = textStyle(30, 42))
        onRetry?.let { CustomButton("다시 시도", it, Modifier.fillMaxWidth()) }
        CustomButton(backLabel, onBack, Modifier.fillMaxWidth())
    }
}
