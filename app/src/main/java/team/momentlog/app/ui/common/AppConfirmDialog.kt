package team.momentlog.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle

@Composable
fun AppConfirmDialog(title: String, message: String, confirmLabel: String, dismissLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(28.q)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.widthIn(max = 760.q).fillMaxWidth(0.9f).background(colors.surface, shape)
            .border(2.q, colors.line, shape).verticalScroll(rememberScrollState()).padding(40.q),
            verticalArrangement = Arrangement.spacedBy(28.q)) {
            Text(title, style = textStyle(38, 50, FontWeight.Bold))
            Text(message, style = textStyle(26, 38, color = colors.textSecondary))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.q)) {
                CustomButton(confirmLabel, onConfirm, Modifier.weight(1f), large = false)
                CustomButton(dismissLabel, onDismiss, Modifier.weight(1f), primary = true, large = false)
            }
        }
    }
}
