package team.momentlog.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color111111
import team.momentlog.app.config.theme.color2F6FDE
import team.momentlog.app.config.theme.colorE5E5E5
import team.momentlog.app.config.theme.colorEAF1FF

/** 차 화면용 큰 버튼. 주요 버튼(primary)은 파랑 테두리 + 연한 파랑 바탕. */
@Composable
fun CustomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = shape,
            border = BorderStroke(2.dp, color2F6FDE),
            colors = ButtonDefaults.buttonColors(containerColor = colorEAF1FF, contentColor = color111111),
        ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            shape = shape,
            border = BorderStroke(1.dp, colorE5E5E5),
        ) { Text(text, fontSize = 17.sp, color = color111111) }
    }
}
