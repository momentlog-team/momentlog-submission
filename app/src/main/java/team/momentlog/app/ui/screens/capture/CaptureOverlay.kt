package team.momentlog.app.ui.screens.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color2F6FDE
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.colorF5F5F5
import team.momentlog.app.config.theme.colorFFFFFF

/**
 * 2 순간 저장. 홈 위에 덮어서 보여 준다. 조작 없이 진행률과 음성 안내만.
 *
 * @param triggerLabel 급정지·기록 요청·에어백
 * @param progress 0.0~1.0. 직전 30초(0.75)까지는 이미 찬 상태로 시작한다.
 * TODO(앱): RecorderService의 저장 진행률 흐름을 받아 표시, TextToSpeech로 "기록했어요" 안내
 */
@Composable
fun CaptureOverlay(triggerLabel: String, progress: Float) {
    Column(
        Modifier.fillMaxSize().background(colorFFFFFF).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("순간을 저장하고 있어요", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = color111111)
        Text(triggerLabel, fontSize = 17.sp, color = color6B6B6B)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(0.7f),
            color = color2F6FDE,
            trackColor = colorF5F5F5,
        )
        Row(Modifier.fillMaxWidth(0.7f), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("직전 30초", fontSize = 14.sp, color = color6B6B6B)
            Text("이후 10초", fontSize = 14.sp, color = color6B6B6B)
        }
        Text(
            "“기록했어요. 차를 세우면 상황을 여쭤볼게요.”",
            fontSize = 17.sp,
            color = color111111,
            modifier = Modifier.background(colorF5F5F5, RoundedCornerShape(24.dp)).padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
