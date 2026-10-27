package team.momentlog.app.ui.screens.widget.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.LocalQhd
import team.momentlog.app.config.theme.MomentlogColors
import team.momentlog.app.ui.models.relativeText
import team.momentlog.core.Snapshot
import team.momentlog.core.TurnSignal
import kotlin.math.ceil
import kotlin.math.max

/** 그래프 위 세로 점선. [said]면 진술에서 나온 사건이라 회색 점선으로 구분한다. */
data class GraphMarker(val seconds: Float, val label: String?, val color: Color, val said: Boolean = false)

/**
 * 속도·브레이크·방향지시등·조향각 시간축 그래프. 0초 = 감지 시점.
 * 신호는 값이 바뀔 때만 오므로 다음 값까지 유지하는 계단식으로 그린다. 값을 못 받은 구간은 그리지 않는다.
 */
@Composable
fun TimelineGraph(
    snapshots: List<Snapshot>,
    triggerAtMillis: Long,
    markers: List<GraphMarker>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    animate: Boolean = true,
    playheadMillis: Long? = null,
) {
    val colors = LocalAppColors.current
    val pxToDp = LocalQhd.current.pxToDp
    val measurer = rememberTextMeasurer()
    val draw = remember(snapshots) { Animatable(if (animate) 0f else 1f) }
    val markerAlpha = remember(snapshots) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(snapshots) {
        if (animate) draw.animateTo(1f, tween(1400, easing = LinearEasing))
    }
    LaunchedEffect(snapshots) {
        if (animate) markerAlpha.animateTo(1f, tween(400, 1100))
    }

    Canvas(modifier) {
        // QHD px → Canvas px
        val u = pxToDp * density
        val graph = Graph(snapshots, triggerAtMillis, colors, measurer, u, compact)
        graph.drawBase(this)
        clipRect(right = graph.gutter + graph.plotWidth * draw.value) { graph.drawData(this) }
        graph.drawMarkers(this, markers, markerAlpha.value)
        graph.drawAxis(this)
        playheadMillis?.let { time ->
            val cursorX = graph.x(time - triggerAtMillis).coerceIn(graph.gutter, graph.gutter + graph.plotWidth)
            drawLine(colors.accent, Offset(cursorX, 0f), Offset(cursorX, size.height - 40 * u), 2 * u)
            drawCircle(colors.accent, 5 * u, Offset(cursorX, 5 * u))
        }
    }
}

private class Graph(
    val snapshots: List<Snapshot>,
    val triggerAt: Long,
    val c: MomentlogColors,
    val measurer: TextMeasurer,
    val u: Float,
    compact: Boolean,
) {
    val fs = (if (compact) 20 else 22) * u
    val gutter = (if (compact) 112 else 150) * u
    val band = (if (compact) 30 else 36) * u
    val steerH = (if (compact) 56 else 72) * u
    val gap = (if (compact) 14 else 18) * u
    val axisH = 44 * u
    val top = 8 * u
    var plotWidth = 0f

    val t0 = (snapshots.firstOrNull()?.atMillis ?: triggerAt) - triggerAt
    val t1 = max((snapshots.lastOrNull()?.atMillis ?: triggerAt) - triggerAt, t0 + 1_000)
    val vmax = niceMax(snapshots.mapNotNull { it.speedKph }.maxOrNull() ?: 0f)

    private var speedTop = 0f
    private var speedH = 0f
    private var brakeTop = 0f
    private var turnTop = 0f
    private var steerTop = 0f
    private var axisTop = 0f

    fun x(rel: Long) = gutter + (rel - t0).toFloat() / (t1 - t0) * plotWidth

    fun layout(scope: DrawScope) {
        plotWidth = scope.size.width - gutter - 24 * u
        axisTop = scope.size.height - axisH
        speedH = axisTop - top - (band * 2 + steerH + gap * 3)
        speedTop = top
        brakeTop = speedTop + speedH + gap
        turnTop = brakeTop + band + gap
        steerTop = turnTop + band + gap
    }

    fun drawBase(scope: DrawScope) = with(scope) {
        layout(this)
        val lanes = listOf(
            Triple("속도", speedTop, speedH) to c.dataSpeed,
            Triple("브레이크", brakeTop, band) to c.dataBrake,
            Triple("방향지시등", turnTop, band) to c.dataTurn,
            Triple("조향각", steerTop, steerH) to c.dataSteer,
        )
        lanes.forEach { (lane, swatch) ->
            val (name, y, h) = lane
            drawRoundRect(c.surface, Offset(gutter, y), Size(plotWidth, h), CornerRadius(8 * u))
            val cy = if (name == "속도") y + 24 * u else y + h / 2
            drawCircle(swatch, 5 * u, Offset(5 * u, cy))
            text(name, Offset(18 * u, cy), fs, FontWeight.Bold, c.textPrimary, centerY = true)
        }
        // 속도 눈금
        listOf(0f, vmax / 2, vmax).forEach { v ->
            val y = speedTop + speedH - v / vmax * speedH
            drawLine(c.line, Offset(gutter, y), Offset(gutter + plotWidth, y), 1 * u)
            text(Math.round(v).toString(), Offset(gutter - 10 * u, y), fs - 4 * u, FontWeight.Normal, c.textTertiary, centerY = true, alignEnd = true)
        }
        text("km/h", Offset(18 * u, speedTop + 50 * u), fs - 4 * u, FontWeight.Normal, c.textTertiary, centerY = true)
        // 조향각 가운데 = 정면 0°
        val mid = steerTop + steerH / 2
        drawLine(c.line, Offset(gutter, mid), Offset(gutter + plotWidth, mid), 1.5f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * u, 6 * u)))
    }

    fun drawData(scope: DrawScope) = with(scope) {
        // 브레이크 띠
        spans { it.brake == true }.forEach { (a, b) ->
            drawRoundRect(c.dataBrake, Offset(x(a), brakeTop), Size(x(b) - x(a), band), CornerRadius(6 * u))
        }
        // 방향지시등 띠 + 화살표(색 없이도 방향 구분)
        val turnSpans = TurnSignal.entries.filter { it != TurnSignal.OFF }.flatMap { side -> spans { s -> s.turn == side }.map { side to it } }
        if (turnSpans.isEmpty()) text("켜지 않음", Offset(gutter + 16 * u, turnTop + band / 2), fs - 2 * u, FontWeight.Normal, c.textTertiary, centerY = true)
        turnSpans.forEach { (side, span) ->
            val (a, b) = span
            val left = x(a)
            val width = x(b) - left
            drawRoundRect(c.dataTurn, Offset(left, turnTop), Size(width, band), CornerRadius(6 * u))
            val n = max(1, (width / (90 * u)).toInt())
            repeat(n) { i ->
                val ax = left + 24 * u + i * ((width - 30 * u) / n)
                val ay = turnTop + band / 2
                val d = 8 * u
                val arrow = Path().apply {
                    if (side == TurnSignal.RIGHT) {
                        moveTo(ax - d, ay - d); lineTo(ax + d / 2, ay); lineTo(ax - d, ay + d)
                    } else {
                        moveTo(ax + d, ay - d); lineTo(ax - d / 2, ay); lineTo(ax + d, ay + d)
                    }
                }
                drawPath(arrow, Color(0xFF10151B), style = Stroke(3 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // 조향각 (−540~540°, 왼쪽 음수가 아래)
        stepLine({ it.steeringDeg }, steerTop + 6 * u, steerH - 12 * u, -STEER_MAX, STEER_MAX, c.dataSteer, 4 * u)
        // 속도
        stepLine({ it.speedKph }, speedTop + 8 * u, speedH - 16 * u, 0f, vmax, c.dataSpeed, 5 * u)
    }

    fun drawMarkers(scope: DrawScope, markers: List<GraphMarker>, alpha: Float) = with(scope) {
        markers.forEach { m ->
            val rel = (m.seconds * 1000).toLong()
            if (rel < t0 || rel > t1) return@forEach
            val mx = x(rel)
            val effect = if (m.said) floatArrayOf(2 * u, 8 * u) else floatArrayOf(6 * u, 6 * u)
            drawLine(
                m.color.copy(alpha = alpha), Offset(mx, top), Offset(mx, axisTop),
                (if (m.said) 2 else 3) * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(effect),
            )
        }
    }

    fun drawAxis(scope: DrawScope) = with(scope) {
        val spanSec = (t1 - t0) / 1000f
        val step = listOf(1, 2, 5, 10, 15, 30, 60).firstOrNull { spanSec / it <= 9 } ?: 60
        var t = ceil(t0 / 1000f / step).toInt() * step
        while (t * 1000L <= t1) {
            val tx = x(t * 1000L)
            drawLine(c.textTertiary, Offset(tx, axisTop + 2 * u), Offset(tx, axisTop + 10 * u), 2 * u)
            val zero = t == 0
            text(relativeText(t.toFloat()), Offset(tx, axisTop + 26 * u), fs - 2 * u, if (zero) FontWeight.ExtraBold else FontWeight.Medium,
                if (zero) c.textPrimary else c.textSecondary, centerY = true, centerX = true)
            t += step
        }
    }

    /** [on]이 참인 구간들(기준 시점 상대 ms). */
    private fun spans(on: (Snapshot) -> Boolean): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        var since: Long? = null
        snapshots.forEach { s ->
            val rel = s.atMillis - triggerAt
            val start = since
            if (on(s) && start == null) since = rel
            if (!on(s) && start != null) {
                result += start to rel
                since = null
            }
        }
        since?.let { result += it to t1 }
        return result
    }

    private fun DrawScope.stepLine(value: (Snapshot) -> Float?, y0: Float, h: Float, lo: Float, hi: Float, color: Color, width: Float) {
        val path = Path()
        var open = false
        var lastY = 0f
        snapshots.forEach { s ->
            val v = value(s)
            val px = x(s.atMillis - triggerAt)
            if (v == null) {
                open = false
                return@forEach
            }
            val py = y0 + h - (v.coerceIn(lo, hi) - lo) / (hi - lo) * h
            if (!open) path.moveTo(px, py) else {
                path.lineTo(px, lastY)
                path.lineTo(px, py)
            }
            open = true
            lastY = py
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun DrawScope.text(
        value: String,
        at: Offset,
        size: Float,
        weight: FontWeight,
        color: Color,
        centerY: Boolean = false,
        centerX: Boolean = false,
        alignEnd: Boolean = false,
    ) {
        val layout = measurer.measure(value, TextStyle(fontSize = (size / density / fontScale).sp, fontWeight = weight, color = color))
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val left = when {
            centerX -> at.x - w / 2
            alignEnd -> at.x - w
            else -> at.x
        }
        drawText(layout, topLeft = Offset(left, if (centerY) at.y - h / 2 else at.y))
    }

    private companion object {
        const val STEER_MAX = 540f

        fun niceMax(v: Float): Float {
            val target = max(v * 1.15f, 20f)
            return listOf(20f, 40f, 60f, 80f, 100f, 120f, 160f, 200f).firstOrNull { it >= target } ?: (ceil(target / 50f) * 50f)
        }
    }
}
