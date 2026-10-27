package team.momentlog.app.ui.screens.widget.record

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon
import team.momentlog.core.Snapshot
import team.momentlog.core.TurnSignal
import kotlin.math.abs
import kotlin.math.roundToInt

/** 색면 대신 글자 정렬과 얇은 눈금으로 읽는 차량 신호 계기. 미수신 항목은 숨긴다. */
@Composable
fun SignalTelemetry(snapshot: Snapshot?, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val speed by animateFloatAsState(snapshot?.speedKph ?: 0f, tween(120), label = "speedGauge")
    val steering by animateFloatAsState(snapshot?.steeringDeg ?: 0f, tween(120), label = "steerGauge")
    Column(modifier) {
        if (snapshot?.speedKph != null) {
            Row(Modifier.padding(bottom = 20.q), verticalAlignment = Alignment.Bottom) {
                Text(speed.roundToInt().toString(), style = textStyle(72, 80, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(10.q))
                Text("km/h", style = textStyle(22, 40, color = c.textSecondary))
            }
        }
        if (snapshot?.turn != null) {
            val turn = snapshot.turn
            SignalRow("방향지시등", when (turn) { TurnSignal.LEFT -> "좌측"; TurnSignal.RIGHT -> "우측"; else -> "꺼짐" }) {
                val left by animateColorAsState(if (turn == TurnSignal.LEFT) c.dataTurn else c.textTertiary.copy(alpha = 0.4f), tween(180), label = "leftSignal")
                val right by animateColorAsState(if (turn == TurnSignal.RIGHT) c.dataTurn else c.textTertiary.copy(alpha = 0.4f), tween(180), label = "rightSignal")
                Icon(AppIcon.BACK, 24.q, left)
                Icon(AppIcon.NEXT, 24.q, right)
            }
        }
        if (snapshot?.brake != null) {
            SignalRow("브레이크", if (snapshot.brake == true) "제동 중" else "해제") {
                val tint by animateColorAsState(if (snapshot.brake == true) c.dataBrake else c.textTertiary.copy(alpha = 0.4f), tween(180), label = "brakeSignal")
                Icon(AppIcon.BRAKE, 28.q, tint)
            }
        }
        if (snapshot?.steeringDeg != null) {
            HorizontalDivider(thickness = 1.q, color = c.line.copy(alpha = 0.65f))
            Column(Modifier.padding(top = 18.q)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("조향각", style = textStyle(22, 30, color = c.textSecondary))
                    Text(if (abs(steering) < 5f) "정면 0°" else "${if (steering < 0) "좌" else "우"} ${abs(steering).roundToInt()}°", style = textStyle(26, 34, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"))
                }
                val unit = 1.q
                Canvas(Modifier.fillMaxWidth().height(40.q).padding(top = 12.q)) {
                    val u = unit.toPx()
                    val mid = size.height / 2
                    val inset = 4 * u
                    val width = size.width - inset * 2
                    drawLine(c.line, Offset(inset, mid), Offset(size.width - inset, mid), u)
                    for (i in 0..8) {
                        val x = inset + width * i / 8
                        val half = if (i == 4) 7 * u else 3 * u
                        drawLine(if (i == 4) c.textSecondary else c.line, Offset(x, mid - half), Offset(x, mid + half), u)
                    }
                    val x = inset + width * (0.5f + steering.coerceIn(-540f, 540f) / 1080f)
                    drawLine(c.textPrimary, Offset(x, mid - 8 * u), Offset(x, mid + 8 * u), 3 * u)
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, indicators: @Composable () -> Unit) {
    val c = LocalAppColors.current
    HorizontalDivider(thickness = 1.q, color = c.line.copy(alpha = 0.65f))
    Row(Modifier.fillMaxWidth().height(68.q), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = textStyle(22, 30, color = c.textSecondary), modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) { indicators() }
        Spacer(Modifier.width(20.q))
        Text(value, style = textStyle(24, 32, FontWeight.Medium), textAlign = TextAlign.End, modifier = Modifier.width(104.q))
    }
}
