package team.momentlog.app.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import team.momentlog.app.RecorderService
import team.momentlog.app.config.Routes
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color1F9D55
import team.momentlog.app.config.theme.color2F6FDE
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.color9E9E9E
import team.momentlog.app.config.theme.colorD93B3B
import team.momentlog.app.config.theme.colorE6F6EC
import team.momentlog.app.config.theme.colorEAF1FF
import team.momentlog.app.config.theme.colorFFF4DC
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 모든 화면 공통 틀: 상단 바(시각, 주행 칩, 기록 상태) + 왼쪽 레일(홈·기록·설정).
 * 주행 중에는 레일의 기록·설정을 누를 수 없다.
 */
@Composable
fun AppScaffold(
    currentRoute: String?,
    isDriving: Boolean,
    recorderStatus: RecorderService.Status,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(isDriving, recorderStatus)
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            Rail(currentRoute, isDriving, onNavigate)
            VerticalDivider()
            Box(Modifier.fillMaxSize().padding(24.dp)) { content() }
        }
    }
}

@Composable
private fun TopBar(isDriving: Boolean, recorderStatus: RecorderService.Status) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val clock = remember(now) { SimpleDateFormat("HH:mm", Locale.KOREA).format(now) }

    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Text(clock, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = color111111)
            Spacer(Modifier.width(12.dp))
            Text(
                if (isDriving) "주행 중" else "정차",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color111111,
                modifier = Modifier
                    .background(if (isDriving) colorFFF4DC else colorE6F6EC, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Text(
            "순간기록",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            val (label, dot) = when (recorderStatus) {
                RecorderService.Status.RECORDING -> "기록 중" to color1F9D55
                RecorderService.Status.REPLAYING -> "시연 데이터" to colorD93B3B
                RecorderService.Status.STOPPED -> "꺼져 있음" to color9E9E9E
            }
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = color6B6B6B)
        }
    }
}

@Composable
private fun Rail(currentRoute: String?, isDriving: Boolean, onNavigate: (String) -> Unit) {
    Column(
        Modifier.width(88.dp).fillMaxHeight().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RailItem(Icons.Filled.Home, "홈", currentRoute == Routes.HOME, enabled = true) { onNavigate(Routes.HOME) }
        RailItem(Icons.AutoMirrored.Filled.List, "기록", currentRoute?.startsWith(Routes.RECORDS) == true, enabled = !isDriving) {
            onNavigate(Routes.RECORDS)
        }
        RailItem(Icons.Filled.Settings, "설정", currentRoute == Routes.SETTINGS, enabled = !isDriving) {
            onNavigate(Routes.SETTINGS)
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = when {
        selected -> color2F6FDE
        enabled -> color6B6B6B
        else -> color9E9E9E.copy(alpha = 0.5f)
    }
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (selected) colorEAF1FF else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(label, fontSize = 12.sp, color = if (selected) color111111 else tint)
    }
}
