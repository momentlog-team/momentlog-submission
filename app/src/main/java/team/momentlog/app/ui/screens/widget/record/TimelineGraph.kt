package team.momentlog.app.ui.screens.widget.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.color2F6FDE
import team.momentlog.app.config.theme.color6B6B6B
import team.momentlog.app.config.theme.color9E9E9E
import team.momentlog.app.config.theme.colorD93B3B
import team.momentlog.app.config.theme.colorE5E5E5
import team.momentlog.app.config.theme.colorF2A516
import team.momentlog.core.Snapshot
import team.momentlog.core.TurnSignal

private val LABEL_WIDTH = 80.dp

/**
 * 기록 상세의 4레인 시간축 그래프: 속도(선), 브레이크·방향지시등(켜진 구간 막대), 핸들(선).
 * 빨간 점선이 기준 시점(충돌·급정지). 값을 못 받은 구간은 그리지 않는다.
 */
@Composable
fun TimelineGraph(snapshots: List<Snapshot>, triggerAtMillis: Long, modifier: Modifier = Modifier) {
    if (snapshots.size < 2) {
        Text("그래프를 그릴 신호가 부족해요", color = color6B6B6B, modifier = modifier)
        return
    }
    val range = TimeRange(snapshots.first().atMillis, snapshots.last().atMillis)
    val triggerRatio = range.ratio(triggerAtMillis)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Lane("속도", 96, triggerRatio) { drawValueLine(snapshots, range, color2F6FDE) { it.speedKph } }
        Lane("브레이크", 28, triggerRatio) { drawOnBars(snapshots, range, colorD93B3B) { it.brake == true } }
        Lane("지시등", 28, triggerRatio) {
            drawOnBars(snapshots, range, colorF2A516) { it.turn != null && it.turn != TurnSignal.OFF }
        }
        Lane("핸들", 48, triggerRatio) { drawValueLine(snapshots, range, color9E9E9E, centered = true) { it.steeringDeg } }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(LABEL_WIDTH))
            Text("-${(triggerAtMillis - range.start) / 1000}초", fontSize = 13.sp, color = color6B6B6B, modifier = Modifier.weight(1f))
            Text("+${(range.end - triggerAtMillis) / 1000}초", fontSize = 13.sp, color = color6B6B6B)
        }
    }
}

private class TimeRange(val start: Long, val end: Long) {
    fun ratio(at: Long): Float = ((at - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

@Composable
private fun Lane(label: String, heightDp: Int, triggerRatio: Float, draw: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = color6B6B6B, modifier = Modifier.width(LABEL_WIDTH))
        Canvas(Modifier.weight(1f).height(heightDp.dp)) {
            drawLine(colorE5E5E5, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            draw()
            val x = size.width * triggerRatio
            drawLine(colorD93B3B, Offset(x, 0f), Offset(x, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
    }
}

private fun DrawScope.drawValueLine(
    snapshots: List<Snapshot>,
    range: TimeRange,
    color: Color,
    centered: Boolean = false,
    value: (Snapshot) -> Float?,
) {
    val points = snapshots.mapNotNull { snapshot -> value(snapshot)?.let { snapshot.atMillis to it } }
    if (points.size < 2) return
    val max = points.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1f)
    val path = Path()
    points.forEachIndexed { index, (at, v) ->
        val x = size.width * range.ratio(at)
        val y = if (centered) size.height / 2 * (1 - v / max) else size.height * (1 - v / max)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 4f))
}

private fun DrawScope.drawOnBars(
    snapshots: List<Snapshot>,
    range: TimeRange,
    color: Color,
    isOn: (Snapshot) -> Boolean,
) {
    fun bar(from: Long, to: Long) {
        val left = size.width * range.ratio(from)
        val right = size.width * range.ratio(to)
        drawRoundRect(color, Offset(left, size.height * 0.2f), Size(right - left, size.height * 0.6f), CornerRadius(8f))
    }

    var onSince: Long? = null
    snapshots.forEach { snapshot ->
        val since = onSince
        val on = isOn(snapshot)
        if (on && since == null) onSince = snapshot.atMillis
        if (!on && since != null) {
            bar(since, snapshot.atMillis)
            onSince = null
        }
    }
    onSince?.let { bar(it, range.end) }
}
