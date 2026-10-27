package team.momentlog.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.RecorderService
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color1F9D55
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.colorD93B3B
import team.momentlog.app.config.theme.colorE5E5E5
import team.momentlog.app.config.theme.colorFDECEC
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.MainViewModel

/**
 * 1 홈 · 기록 대기. 주행 중에도 보이는 유일한 화면이라 조작은 "지금 기록" 버튼 하나만 둔다.
 * TODO(앱): 신호 카드 4개에 실시간 값 연결(MainViewModel에 실시간 신호 흐름 추가 후), 최근 30초 속도 그래프
 */
@Composable
fun HomeScreen(mainViewModel: MainViewModel) {
    val status by mainViewModel.recorderStatus.collectAsState()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(if (status == RecorderService.Status.STOPPED) color6B6B6B else color1F9D55, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (status == RecorderService.Status.STOPPED) "기록이 꺼져 있어요" else "지켜보는 중",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color111111,
                )
            }
            Text("최근 30초만 메모리에 두고 계속 덮어써요. 평소엔 저장하지 않아요.", fontSize = 15.sp, color = color6B6B6B)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SignalCard("속도", "-", Modifier.weight(1f))
                SignalCard("브레이크", "-", Modifier.weight(1f))
                SignalCard("방향지시등", "-", Modifier.weight(1f))
                SignalCard("핸들", "-", Modifier.weight(1f))
            }
            if (status == RecorderService.Status.STOPPED) {
                CustomButton("기록 시작", onClick = { mainViewModel.startRecording(replay = false) }, primary = true)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RecordNowButton(onClick = mainViewModel::recordNow)
            Text("주행 중엔 버튼 하나와 음성만 써요", fontSize = 14.sp, color = color6B6B6B)
        }
    }
}

@Composable
private fun SignalCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.border(BorderStroke(1.dp, colorE5E5E5), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = 14.sp, color = color6B6B6B)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color111111)
    }
}

@Composable
private fun RecordNowButton(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(colorFDECEC, RoundedCornerShape(20.dp))
            .border(BorderStroke(2.dp, colorD93B3B), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).background(colorD93B3B, CircleShape))
        Spacer(Modifier.height(20.dp))
        Text("지금 기록", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color111111)
        Spacer(Modifier.height(8.dp))
        Text("또는 “기록해”라고 말하기", fontSize = 15.sp, color = color6B6B6B)
    }
}
