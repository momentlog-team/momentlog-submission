package team.momentlog.app.ui.screens.widget.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * 선 아이콘(24×24 SVG 경로). Material 아이콘 확장 라이브러리를 들이지 않으려고 직접 그린다.
 */
enum class AppIcon(val stroke: String, val fill: String? = null, val width: Float = 2.2f) {
    MIC("M10 2h4a1 1 0 0 1 1 1v10a3 3 0 0 1-6 0V3a1 1 0 0 1 1-1z M5 10v1a7 7 0 0 0 14 0v-1 M12 18v4", width = 2f),
    REC(circle(12f, 12f, 9f), fill = circle(12f, 12f, 4f), width = 2f),
    INFO(circle(12f, 12f, 9f) + " M12 11v6", fill = circle(12f, 7.5f, 1f), width = 2f),
    CHECK(circle(12f, 12f, 9f) + " M8 12.5l2.6 2.6L16 9.5", width = 2.4f),
    OK("M5 12.5l4.5 4.5L19 7.5", width = 2.6f),
    CLOCK(circle(12f, 12f, 9f) + " M12 7v5l3 2", width = 2.4f),
    LOCK("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z M8 11V8a4 4 0 0 1 8 0v3"),
    IMPACT("M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M18.4 5.6l-2.8 2.8M8.4 15.6l-2.8 2.8", width = 2.4f),
    STOP("M12 4v10M7 9l5 5 5-5M5 20h14", width = 2.4f),
    QR("M4.5 3h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 8.5 10h-4A1.5 1.5 0 0 1 3 8.5v-4A1.5 1.5 0 0 1 4.5 3z M15.5 3h4A1.5 1.5 0 0 1 21 4.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 14 8.5v-4A1.5 1.5 0 0 1 15.5 3z M4.5 14h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 8.5 21h-4A1.5 1.5 0 0 1 3 19.5v-4A1.5 1.5 0 0 1 4.5 14z M14 14h3v3h-3z M20 14v.01M14 20h.01M17 20h4v-3"),
    LIST("M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"),
    PEN("M4 20h4L19 9l-4-4L4 16z"),
    REDO("M4 12a8 8 0 1 0 2.4-5.7L4 8.5 M4 4v4.5h4.5", width = 2.4f),
    OFFLINE("M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0M12 19h.01M3 3l18 18", width = 2f),
    WARN("M12 6v8M12 18h.01", width = 2.6f),
    BACK("M15 5l-7 7 7 7", width = 2.4f),
    HOME("M3 10l9-7 9 7M5 9v12h5v-7h4v7h5V9", width = 2.2f),
    PLAY("M8 4l12 8-12 8z", fill = "M8 4l12 8-12 8z", width = 1.4f),
    PAUSE("M8 5v14M16 5v14", width = 3.2f),
    BRAKE(circle(12f, 12f, 6f) + " M4 5a10 10 0 0 0 0 14M20 5a10 10 0 0 1 0 14M12 8v5M12 16v.01", width = 2f),
    NEXT("M9 6l6 6-6 6", width = 2.4f),
    X("M6 6l12 12M18 6L6 18", width = 2.8f),
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

@Composable
fun Icon(icon: AppIcon, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val stroke = remember(icon) { PathParser().parsePathString(icon.stroke).toPath() }
    val fill = remember(icon) { icon.fill?.let { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(stroke, color, style = Stroke(width = icon.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            fill?.let { drawPath(it, color, style = Fill) }
        }
    }
}

fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
