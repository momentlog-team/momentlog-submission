package team.momentlog.app.ui.screens.widget.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon

@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(20.q)
    Box(
        modifier
            .size(80.q)
            .shadow(3.q, shape)
            .background(colors.surface, shape)
            .semantics { contentDescription = "홈으로 이동" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcon.HOME, 36.q, colors.textPrimary)
    }
}
