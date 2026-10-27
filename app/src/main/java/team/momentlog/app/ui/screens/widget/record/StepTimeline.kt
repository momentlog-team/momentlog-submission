package team.momentlog.app.ui.screens.widget.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.models.relativeText

/** 경위 단계 하나. [said]면 센서가 아니라 진술에서 나온 사건. */
data class TimelineStep(val seconds: Float, val text: String, val color: Color, val said: Boolean = false)

/**
 * 경위 단계를 가로 시간선 위에 놓는다. [compact]면 한 줄씩 세로로 쌓는다.
 */
@Composable
fun StepTimeline(steps: List<TimelineStep>, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (compact) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(20.q)) {
            steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(step, 22)
                    Spacer(Modifier.width(14.q))
                    Text(relativeText(step.seconds), style = textStyle(28, 34, FontWeight.ExtraBold))
                    Spacer(Modifier.width(12.q))
                    Text(step.text + if (step.said) " (진술)" else "", style = textStyle(22, 30, color = LocalAppColors.current.textSecondary))
                }
            }
        }
        return
    }
    val colors = LocalAppColors.current
    Box(modifier.fillMaxWidth()) {
        val unit = 1.q
        Canvas(Modifier.fillMaxWidth().height(30.q)) {
            drawLine(colors.line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4 * unit.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            steps.forEach { step ->
                Column(Modifier.weight(1f)) {
                    Dot(step, 30)
                    Spacer(Modifier.height(20.q))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(relativeText(step.seconds), style = textStyle(40, 48, FontWeight.ExtraBold))
                        if (step.said) {
                            Spacer(Modifier.width(10.q))
                            Text(
                                "진술",
                                style = textStyle(18, 22, FontWeight.Bold, colors.textSecondary),
                                modifier = Modifier.border(2.q, colors.textTertiary, CircleShape).padding(horizontal = 12.q, vertical = 4.q),
                            )
                        }
                    }
                    Text(step.text, style = textStyle(24, 32, color = colors.textSecondary), modifier = Modifier.padding(end = 16.q))
                }
            }
        }
    }
}

@Composable
private fun Dot(step: TimelineStep, size: Int) {
    val colors = LocalAppColors.current
    if (step.said) {
        val unit = 1.q
        Canvas(Modifier.size(size.q)) {
            drawCircle(colors.bg)
            drawCircle(
                colors.textTertiary, radius = this.size.minDimension / 2 - 2 * unit.toPx(),
                style = Stroke(3 * unit.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * unit.toPx(), 4 * unit.toPx()))),
            )
        }
    } else {
        Box(Modifier.size(size.q).background(colors.bg, CircleShape).padding(6.q).background(step.color, CircleShape))
    }
}
