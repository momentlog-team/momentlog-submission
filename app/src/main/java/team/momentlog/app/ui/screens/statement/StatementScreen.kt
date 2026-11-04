package team.momentlog.app.ui.screens.statement

import android.Manifest
import androidx.activity.compose.BackHandler
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.common.RecoveryPanel
import team.momentlog.app.ui.screens.widget.layout.HomeButton
import team.momentlog.app.ui.models.Formats
import team.momentlog.app.ui.models.RecordInfo
import team.momentlog.app.ui.models.RecordKind
import team.momentlog.app.ui.screens.use_case.StatementViewModel
import team.momentlog.app.ui.screens.use_case.StatementViewModel.Step
import team.momentlog.app.ui.screens.widget.car.ImpactArc
import team.momentlog.app.ui.screens.widget.car.RoadStage
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.label.PlateBadge
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.label.VoiceHint
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow

/**
 * 정차 후 음성 진술.
 */
@Composable
fun StatementScreen(
    record: RecordInfo?,
    recordId: String,
    statementViewModel: StatementViewModel,
    onSaved: () -> Unit,
    onOpenRecords: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(recordId) { statementViewModel.start(recordId) }
    val step by statementViewModel.step.collectAsState()
    val transcript by statementViewModel.transcript.collectAsState()
    val partial by statementViewModel.partial.collectAsState()
    val plate by statementViewModel.plate.collectAsState()
    val session by statementViewModel.session.collectAsState()
    val voiceError by statementViewModel.voiceError.collectAsState()
    val ready by statementViewModel.ready.collectAsState()
    val level by statementViewModel.level.collectAsState()
    val editText by statementViewModel.editText.collectAsState()
    val editPlate by statementViewModel.editPlate.collectAsState()
    val recordAvailable by statementViewModel.recordAvailable.collectAsState()
    var confirmExit by remember(recordId) { mutableStateOf(false) }
    val requestExit: () -> Unit = {
        if (transcript.isNotBlank() || partial.isNotBlank() || step == Step.EDITING && editText.isNotBlank()) confirmExit = true
        else { statementViewModel.leaveScreen(recordId); onClose() }
    }
    BackHandler {
        if (step == Step.EDITING) statementViewModel.cancelEdit() else requestExit()
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("진술 작성을 나갈까요?") },
        text = { Text(if (voiceError != null) "저장하지 못한 내용이 있을 수 있어요. 나가면 해당 내용이 사라질 수 있어요." else "작성 중인 내용은 초안으로 보관해요. 기록 목록에서 이어서 작성할 수 있어요.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; statementViewModel.leaveScreen(recordId); onClose() }) { Text("나가기") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 작성") } },
    )
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) statementViewModel.beginRecognition()
        else if (statementViewModel.step.value == Step.LISTENING) statementViewModel.recognitionFailed("마이크 권한을 허용해야 받아쓸 수 있어요. 말하기를 눌러 다시 시도해 주세요.")
    }
    LaunchedEffect(step, session, recordAvailable) {
        if (recordAvailable != true) return@LaunchedEffect
        if (step == Step.LISTENING) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                statementViewModel.beginRecognition()
            } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (step == Step.ASK && voiceError == null) statementViewModel.prompt()
    }
    DisposableEffect(lifecycleOwner, recordId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) statementViewModel.leaveScreen(recordId)
            if (event == Lifecycle.Event.ON_START) statementViewModel.start(recordId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            statementViewModel.leaveScreen(recordId)
        }
    }
    val colors = LocalAppColors.current
    val split = isSplit
    val kind = record?.kind ?: RecordKind.HARD_STOP
    val tone = kind.tone(colors)

    if (recordAvailable != true) {
        AppScreen(drivingAllowed = false) {
            RecoveryPanel(
                if (recordAvailable == null) "기록을 불러오고 있어요" else voiceError ?: "기록을 찾을 수 없어요.",
                "기록 목록으로", onOpenRecords, Modifier.align(Alignment.Center),
                onRetry = if (recordAvailable == false) ({ statementViewModel.start(recordId) }) else null,
            )
        }
        return
    }

    if (step == Step.EDITING) {
        AppScreen(drivingAllowed = false) {
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(48.q), verticalArrangement = Arrangement.spacedBy(20.q)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("진술 고치기", style = textStyle(44, 56, FontWeight.Bold), modifier = Modifier.weight(1f))
                    TextButton(onClick = requestExit) { Text("닫기", style = textStyle(26, 34)) }
                }
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.line,
                    focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.accent,
                )
                OutlinedTextField(editText, statementViewModel::updateEditText,
                    modifier = Modifier.fillMaxWidth(), label = { Text("진술 내용") },
                    minLines = 3, textStyle = textStyle(28, 38), colors = fieldColors)
                OutlinedTextField(editPlate, statementViewModel::updateEditPlate,
                    modifier = Modifier.fillMaxWidth(), label = { Text("상대 차량 번호 (선택)") },
                    singleLine = true, textStyle = textStyle(28, 38), colors = fieldColors)
                voiceError?.let { Text(it, style = textStyle(24, 32, color = colors.warning)) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.q)) {
                    CustomButton("수정 확인", statementViewModel::confirmEdit, primary = true)
                    CustomButton("취소", statementViewModel::cancelEdit)
                }
                Text("수정 확인 후 ‘이대로 저장’을 누르면 저장돼요", style = textStyle(24, 32, color = colors.textSecondary))
            }
        }
        return
    }

    AppScreen(drivingAllowed = false) {
        RoadStage(
            parked = true,
            dim = step == Step.LISTENING,
            behindCar = { g ->
                val unit = 1.q
                Canvas(Modifier.fillMaxSize()) {
                    val u = unit.toPx()
                    drawRect(tone.copy(alpha = 0.22f), Offset(g.roadX * u, g.carRearY * u), Size(g.roadWidth * u, size.height - (g.carRearY + 40) * u))
                    drawRect(tone, Offset((g.roadX - 10) * u, (g.carRearY - 3) * u), Size((g.roadWidth + 20) * u, 6 * u))
                }
            },
            overCar = { g ->
                if (kind == RecordKind.IMPACT) ImpactArc(rear = step != Step.LATER, g = g)
                if (record != null) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = if (split) 56.q else 590.q, bottom = 120.q),
                    ) {
                        Text("${Formats.clock(record.triggeredAtMillis)} 저장됨", style = textStyle(24, 30, FontWeight.Bold, tone))
                        Text(kind.label, style = textStyle(20, 26, color = colors.textSecondary))
                    }
                }
            },
        )

        Column(
            (if (split) Modifier.padding(start = 360.q, end = 48.q, top = 112.q) else Modifier.fillMaxHeight().padding(start = 820.q, end = 80.q, bottom = 200.q))
                .then(if (step == Step.ASK || step == Step.LATER) Modifier else Modifier.enterFromBelow(16.q, key = step)),
            verticalArrangement = if (split) Arrangement.Top else Arrangement.Center,
        ) {
            when (step) {
                Step.ASK -> Ask()
                Step.LISTENING -> Listening(transcript, partial, ready, level)
                Step.EDITING -> Unit
                Step.CONFIRM -> Confirm(transcript, plate)
                Step.LATER -> Later()
            }
            voiceError?.let {
                Spacer(Modifier.height(24.q))
                Text(it, style = textStyle(24, 34, color = colors.warning))
            }
        }

        val actions: List<Triple<String, AppIcon?, () -> Unit>> = when (step) {
            Step.ASK -> listOf(
                Triple("말하기", AppIcon.MIC, statementViewModel::speak),
                Triple("직접 입력", null, statementViewModel::edit),
                Triple("나중에", null, statementViewModel::later),
            )
            Step.LISTENING -> listOf(
                Triple("다 말했어요", AppIcon.OK, statementViewModel::finishSpeaking),
                Triple("처음부터", AppIcon.REDO, statementViewModel::restart),
            )
            Step.CONFIRM -> listOf(
                Triple("이대로 저장", AppIcon.OK, { statementViewModel.save(onSaved) }),
                Triple("고치기", null, statementViewModel::edit),
                Triple("다시 말하기", AppIcon.REDO, statementViewModel::restart),
            )
            Step.EDITING -> emptyList()
            Step.LATER -> listOf(
                Triple("기록 보기", AppIcon.LIST, onOpenRecords),
                Triple("닫기", null, onClose),
            )
        }
        val bottom = Modifier
            .align(Alignment.BottomStart)
            .padding(start = if (split) 360.q else 820.q, end = if (split) 48.q else 80.q, bottom = if (split) 80.q else 88.q)
        if (split) {
            Column(bottom.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.q)) {
                actions.forEachIndexed { i, (label, icon, onClick) ->
                    StatementReveal(560 + i * 100, animationKey = step) {
                        CustomButton(label, onClick, Modifier.fillMaxWidth(), primary = i == 0, icon = icon)
                    }
                }
            }
        } else {
            Row(bottom, horizontalArrangement = Arrangement.spacedBy(16.q)) {
                actions.forEachIndexed { i, (label, icon, onClick) ->
                    StatementReveal(560 + i * 100, animationKey = step) {
                        CustomButton(label, onClick, primary = i == 0, icon = icon)
                    }
                }
            }
        }
        HomeButton(onClick = requestExit, modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.q, end = 40.q))
    }
}

@Composable
private fun ColumnScope.Ask() {
    val colors = LocalAppColors.current
    StatementReveal(0) {
        StatusChip("기록을 저장했어요", colors.recording, icon = AppIcon.CHECK)
    }
    Spacer(Modifier.height(32.q))
    StatementReveal(140) {
        Text("괜찮으세요?", style = textStyle(60, 80, FontWeight.ExtraBold))
    }
    Spacer(Modifier.height(16.q))
    StatementReveal(280) {
        Text("상황을 말씀해 주시면 기록해 둘게요", style = textStyle(40, 52, color = colors.textSecondary))
    }
    Spacer(Modifier.height(48.q))
    StatementReveal(420) {
        VoiceHint(null, "말하기를 누르고 상황을 말씀해 주세요")
    }
}

/** 문구의 자리 아래에서 글자가 올라오며 드러난다. 받아쓰기 갱신에는 재생하지 않는다. */
@Composable
private fun StatementReveal(delayMillis: Int, animationKey: Any? = Unit, content: @Composable () -> Unit) {
    val progress = remember(animationKey) { Animatable(0f) }
    val distance = with(LocalDensity.current) { 24.q.toPx() }
    LaunchedEffect(animationKey) {
        progress.animateTo(1f, tween(900, delayMillis, CubicBezierEasing(0.22f, 0.1f, 0.22f, 1f)))
    }
    Box(Modifier.clipToBounds()) {
        Box(Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * distance
        }) { content() }
    }
}

@Composable
private fun ColumnScope.Listening(transcript: String, partial: String, ready: Boolean, level: Float) {
    val colors = LocalAppColors.current
    StatusChip(if (ready) "듣고 있어요" else "마이크 준비 중", colors.accent, icon = AppIcon.MIC)
    Spacer(Modifier.height(32.q))
    val cursorAlpha by rememberInfiniteTransition(label = "cursor")
        .animateFloat(1f, 0f, InfiniteRepeatableSpec(tween(500), RepeatMode.Reverse), label = "cursorAlpha")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            buildAnnotatedString {
                append(transcript)
                if (transcript.isNotEmpty() && partial.isNotEmpty()) append(" ")
                withStyle(SpanStyle(color = colors.textTertiary)) { append(partial) }
            },
            style = textStyle(40, 56, FontWeight.Bold),
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(Modifier.padding(start = 6.q, bottom = 6.q).size(4.q, 44.q).background(colors.accent.copy(alpha = cursorAlpha)))
    }
    Spacer(Modifier.height(40.q))
    SoundBars(if (ready) level else 0f)
    Spacer(Modifier.height(40.q))
    VoiceHint(null, "상대 차 번호도 말씀해 주세요")
}

/** 소리 막대. 반복 모션은 정차 중 화면에만 쓴다. */
@Composable
private fun SoundBars(inputLevel: Float) {
    val colors = LocalAppColors.current
    Row(Modifier.height(96.q), horizontalArrangement = Arrangement.spacedBy(10.q), verticalAlignment = Alignment.CenterVertically) {
        repeat(14) { i ->
            val level by androidx.compose.animation.core.animateFloatAsState(
                (0.12f + inputLevel * (0.45f + ((i * 7) % 11) / 20f)).coerceIn(0.12f, 1f), tween(100), label = "bar$i",
            )
            Box(Modifier.width(12.q).fillMaxHeight(level).background(colors.accent, RoundedCornerShape(6.q)))
        }
    }
}

@Composable
private fun ColumnScope.Confirm(transcript: String, plate: String?) {
    val colors = LocalAppColors.current
    Text("이렇게 적어 둘게요", style = textStyle(60, 80, FontWeight.ExtraBold))
    Spacer(Modifier.height(32.q))
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.q))
            .padding(horizontal = 44.q, vertical = 40.q),
    ) {
        Text("진술 · 운전자", style = textStyle(20, 26, FontWeight.Bold, colors.textTertiary))
        Spacer(Modifier.height(12.q))
        Text(transcript.ifBlank { "받아 적은 내용이 없어요" }, style = textStyle(30, 46, color = if (transcript.isBlank()) colors.textTertiary else colors.textPrimary))
    }
    Spacer(Modifier.height(32.q))
    Text("상대 차량 번호", style = textStyle(24, 32, FontWeight.Bold, colors.textSecondary))
    Spacer(Modifier.height(12.q))
    if (plate != null) {
        PlateBadge(plate, height = 88, fontSize = 44)
    } else {
        Text("말씀하신 번호가 없어요", style = textStyle(30, 42, color = colors.textTertiary))
    }
}

@Composable
private fun ColumnScope.Later() {
    val colors = LocalAppColors.current
    StatementReveal(0) {
        StatusChip("진술 미작성", colors.warning, icon = AppIcon.CLOCK)
    }
    Spacer(Modifier.height(32.q))
    StatementReveal(140) {
        Text("알겠어요, 나중에 남겨도 돼요", style = textStyle(60, 80, FontWeight.ExtraBold))
    }
    Spacer(Modifier.height(16.q))
    StatementReveal(300) {
        Text(
            "기록은 저장해 두었어요. 기록 목록에서 정차 중 언제든 진술을 추가할 수 있어요.",
            style = textStyle(30, 42, color = colors.textSecondary),
        )
    }
}
