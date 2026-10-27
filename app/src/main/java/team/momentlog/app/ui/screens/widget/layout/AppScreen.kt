package team.momentlog.app.ui.screens.widget.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.ui.screens.widget.label.DriveFlag

/**
 * 차 화면 공통 바탕. 오른쪽 아래에 주행 중 표시 가능 여부를 적는다.
 */
@Composable
fun AppScreen(drivingAllowed: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(LocalAppColors.current.bg)) {
        content()
        DriveFlag(drivingAllowed, Modifier.align(Alignment.BottomEnd).padding(end = 28.q, bottom = 20.q))
    }
}

/** 왼쪽 패널. */
@Composable
fun SidePanel(width: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.width(width.q).fillMaxHeight().background(LocalAppColors.current.surfaceSunk), content = content)
}
