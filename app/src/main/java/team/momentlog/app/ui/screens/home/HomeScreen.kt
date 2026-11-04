package team.momentlog.app.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.widget.car.RoadStage
import team.momentlog.app.ui.screens.widget.car.TrailBehindCar
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.label.InfoNote
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.label.VoiceHint
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow
import team.momentlog.app.record.SyncStatus

/**
 * 대기 화면. 주행 중에도 보이는 화면이라 조작은 "지금 기록" 버튼 하나와 음성만 둔다.
 */
@Composable
fun HomeScreen(mainViewModel: MainViewModel, onOpenRecords: () -> Unit, onOpenSettings: () -> Unit) {
    val isDriving by mainViewModel.isDriving.collectAsState()
    val unsupported by mainViewModel.unsupportedSignals.collectAsState()
    val syncStatus by mainViewModel.syncStatus.collectAsState()
    HomeContent(
        parked = !isDriving,
        unsupported = unsupported,
        onRecordNow = mainViewModel::recordNow,
        onOpenRecords = onOpenRecords,
        syncStatus = syncStatus,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun HomeContent(parked: Boolean, unsupported: List<String>, onRecordNow: () -> Unit, onOpenRecords: () -> Unit, syncStatus: SyncStatus, onOpenSettings: () -> Unit) {
    val colors = LocalAppColors.current
    val split = isSplit
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        arrival.animateTo(1f, tween(1800, easing = CubicBezierEasing(0.18f, 0.55f, 0.25f, 1f)))
    }
    AppScreen(drivingAllowed = !parked) {
        RoadStage(
            parked = parked,
            arrivalProgress = arrival.value,
            behindCar = { g ->
                if (arrival.value >= 0.999f) {
                    TrailBehindCar(g)
                    TrailLabel(split)
                }
            },
            overCar = { if (!split) SignalCallouts(unsupported, ready = arrival.value >= 0.999f) },
        )

        val copyModifier = if (split) {
            Modifier.padding(start = 360.q, end = 48.q, top = 120.q)
        } else {
            Modifier.fillMaxHeight().padding(start = 820.q, end = 80.q)
        }
        Column(
            copyModifier.enterFromBelow(16.q, 80),
            verticalArrangement = if (split) Arrangement.Top else Arrangement.Center,
        ) {
            if (parked) {
                StatusChip("정차 중", colors.textSecondary, background = colors.surface)
            } else {
                StatusChip("기록 대기", colors.recording, dot = true)
            }
            Spacer(Modifier.height(32.q))
            Text(
                "최근 3분을 지키고 있어요",
                style = textStyle(if (split) 48 else 60, if (split) 64 else 80, FontWeight.ExtraBold),
            )
            Spacer(Modifier.height(16.q))
            Text(
                if (parked) "방금 일이 기록되지 않았다면 지금 남길 수 있어요" else "평소에는 아무것도 저장하지 않아요",
                style = textStyle(30, 42, color = colors.textSecondary),
            )
            if (parked) {
                InfoNote("시동을 끄면 최근 3분은 지워져요", Modifier.padding(top = 24.q))
            } else if (unsupported.isNotEmpty() && !split) {
                InfoNote("${withTopic(unsupported.joinToString("·"))} 이 차에서 기록할 수 없어요", Modifier.padding(top = 24.q))
            }
            if (split) SignalList(unsupported)
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (split) 360.q else 820.q, end = if (split) 48.q else 80.q, bottom = if (split) 80.q else 88.q)
                .enterFromBelow(16.q, 80),
            verticalArrangement = Arrangement.spacedBy(32.q),
        ) {
            val buttonModifier = if (split) Modifier.fillMaxWidth() else Modifier
            if (parked) {
                CustomButton("방금 일 기록", onRecordNow, buttonModifier, primary = true, icon = AppIcon.REC)
            } else {
                CustomButton("지금 기록", onRecordNow, buttonModifier, icon = AppIcon.REC)
            }
        }

        if (parked) {
            if (syncStatus.needsAttention || syncStatus.pending > 0) StatusChip(
                if (syncStatus.needsAttention) "서버 전송 확인 필요" else "서버 전송 대기",
                if (syncStatus.needsAttention) colors.warning else colors.textSecondary,
                Modifier.align(Alignment.TopStart).padding(start = if (split) 40.q else 56.q, top = 56.q).clickable(onClick = onOpenSettings),
                icon = if (syncStatus.needsAttention) AppIcon.WARN else AppIcon.OFFLINE,
            )
            CustomButton(
                "내 기록", onOpenRecords,
                Modifier.align(Alignment.TopEnd).padding(top = 44.q, end = if (split) 48.q else 56.q),
                large = false, icon = AppIcon.LIST,
            )
        }
    }
}

@Composable
private fun BoxScope.TrailLabel(split: Boolean) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = if (split) 56.q else 590.q, bottom = if (split) 56.q else 120.q),
    ) {
        Text("지나온 3분", style = textStyle(24, 30, FontWeight.Bold, colors.accent))
        Spacer(Modifier.height(4.q))
        Text("메모리에만 있어요", style = textStyle(20, 26, FontWeight.Medium, colors.textSecondary))
    }
}

/** 차 그림의 램프·바퀴에서 신호 이름으로 이어지는 라벨. */
@Composable
private fun BoxScope.SignalCallouts(unsupported: List<String>, ready: Boolean) {
    val colors = LocalAppColors.current
    val unit = 1.q
    val reveals = remember { List(4) { Animatable(0f) } }
    LaunchedEffect(ready) {
        if (!ready) {
            reveals.forEach { it.snapTo(0f) }
        } else {
            delay(120)
            reveals.forEach {
                it.animateTo(1f, tween(300))
                delay(80)
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val u = unit.toPx()
        listOf(
            Offset(459f, 236f) to 590f, Offset(423f, 597f) to 590f,
            Offset(302f, 290f) to 200f, Offset(306f, 420f) to 200f,
        ).forEachIndexed { index, (from, toX) ->
            val progress = reveals[index].value
            drawLine(colors.textTertiary.copy(alpha = progress), Offset(from.x * u, from.y * u), Offset((from.x + (toX - from.x) * progress) * u, from.y * u), 2 * u)
            drawCircle(colors.textPrimary.copy(alpha = progress), 6 * u, Offset(from.x * u, from.y * u))
        }
    }
    Callout("방향지시등", null, "방향지시등" in unsupported, left = 604.q, top = 218.q, reveal = reveals[0].value)
    Callout("브레이크", null, "브레이크" in unsupported, left = 604.q, top = 579.q, reveal = reveals[1].value)
    val steerOff = "조향각" in unsupported
    Callout("조향각", if (steerOff) "기록 불가" else null, steerOff, rightEdge = 188.q, top = if (steerOff) 256.q else 272.q, reveal = reveals[2].value)
    val speedOff = "속도" in unsupported
    Callout("속도", if (speedOff) "기록 불가" else "위치·진행 방향", speedOff, rightEdge = 188.q, top = 388.q, reveal = reveals[3].value)
}

@Composable
private fun Callout(name: String, sub: String?, off: Boolean, top: Dp, left: Dp? = null, rightEdge: Dp? = null, reveal: Float = 1f) {
    val colors = LocalAppColors.current
    val align = if (left != null) TextAlign.Start else TextAlign.End
    val width = 180.q
    Column(
        Modifier.offset(x = left ?: (rightEdge!! - width), y = top + (10f * (1f - reveal)).q).width(width).alpha(((reveal - 0.3f) / 0.7f).coerceIn(0f, 1f)),
        horizontalAlignment = if (left != null) Alignment.Start else Alignment.End,
    ) {
        Text(name, style = textStyle(24, 36, FontWeight.SemiBold, if (off) colors.textTertiary else colors.textPrimary), textAlign = align)
        if (sub != null) Text(sub, style = textStyle(20, 26, FontWeight.Medium, colors.textTertiary), textAlign = align)
    }
}

/** 세로 분할 창은 라벨 자리가 없어 목록으로 보여 준다. */
@Composable
private fun SignalList(unsupported: List<String>) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(top = 32.q), verticalArrangement = Arrangement.spacedBy(14.q)) {
        listOf("방향지시등", "브레이크", "조향각", "속도").forEachIndexed { index, name ->
            val off = name in unsupported
            Row(Modifier.enterFromBelow(10.q, delayMillis = 1920 + index * 380), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.q)
                        .then(if (off) Modifier.border(2.q, colors.textTertiary, CircleShape) else Modifier.background(colors.recording, CircleShape)),
                )
                Spacer(Modifier.width(10.q))
                Text(
                    if (off) "$name 기록 불가" else name,
                    style = textStyle(24, 32, color = if (off) colors.textTertiary else colors.textSecondary),
                )
            }
        }
    }
}

/** 받침 유무로 은/는. */
private fun withTopic(word: String): String {
    val last = word.lastOrNull() ?: return word
    val hasBatchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return word + if (hasBatchim) "은" else "는"
}
