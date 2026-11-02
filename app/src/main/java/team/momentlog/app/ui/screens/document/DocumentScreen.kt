package team.momentlog.app.ui.screens.document

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.isSplit
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.common.CustomButton
import team.momentlog.app.ui.models.Formats
import team.momentlog.app.ui.screens.use_case.DocumentViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.app.ui.screens.widget.icon.Icon
import team.momentlog.app.ui.screens.widget.label.StatusChip
import team.momentlog.app.ui.screens.widget.layout.AppScreen
import team.momentlog.app.ui.screens.widget.layout.HomeButton
import team.momentlog.app.ui.screens.widget.layout.SidePanel
import team.momentlog.app.ui.screens.widget.motion.enterFromBelow
import team.momentlog.app.ui.screens.widget.qr.QrCode

/**
 * 휴대폰으로 받기(QR).
 */
@Composable
fun DocumentScreen(recordId: String, recordViewModel: RecordViewModel, documentViewModel: DocumentViewModel, onBack: () -> Unit, onHome: () -> Unit) {
    LaunchedEffect(recordId) { recordViewModel.loadRecord(recordId) }
    val record by recordViewModel.selected.collectAsState()
    val online by documentViewModel.online.collectAsState()
    val openedAt by documentViewModel.openedAtMillis.collectAsState()
    val share by documentViewModel.share.collectAsState()
    val failure by documentViewModel.failure.collectAsState()
    val current = record?.takeIf { it.id == recordId }

    LaunchedEffect(current, online) {
        if (online) current?.let(documentViewModel::createDocument)
    }

    val colors = LocalAppColors.current
    val split = isSplit
    AppScreen(drivingAllowed = false) {
        val panelHeight = if (split) 560.q else null
        val qrState = when {
            !online -> QrState.OFFLINE
            failure != null -> QrState.ERROR
            share?.recordId != recordId -> QrState.LOADING
            openedAt != null -> QrState.OPENED
            else -> QrState.READY
        }
        val qrPanel: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QrCard(qrState, documentViewModel.documentUrl(recordId), if (split) 420 else 560, if (split) 360 else 480)
                Spacer(Modifier.height(24.q))
                Text("문서에는 경위 문장과 그래프, 원본 대조 QR이 들어가요", style = textStyle(24, 32, color = colors.textSecondary), textAlign = TextAlign.Center)
            }
        }
        if (split) {
            Box(Modifier.fillMaxWidth().height(panelHeight!!).background(colors.surfaceSunk)) { qrPanel() }
        } else {
            SidePanel(700) { qrPanel() }
        }

        Column(
            Modifier
                .padding(start = if (split) 40.q else 748.q, end = if (split) 40.q else 64.q, top = if (split) 600.q else 44.q)
                .enterFromBelow(16.q, key = qrState),
        ) {
            when (qrState) {
                QrState.LOADING -> StatusChip("문서 준비 중", colors.accent, icon = AppIcon.QR)
                QrState.ERROR -> StatusChip("문서 생성 실패", colors.warning, icon = AppIcon.OFFLINE)
                QrState.READY -> StatusChip("휴대폰으로 받기", colors.accent, icon = AppIcon.QR)
                QrState.OPENED -> StatusChip("휴대폰에서 열었어요 · ${Formats.clock(openedAt!!)}", colors.recording, icon = AppIcon.CHECK)
                QrState.OFFLINE -> StatusChip("통신 연결 안 됨", colors.warning, icon = AppIcon.OFFLINE)
            }
            Spacer(Modifier.height(if (split) 24.q else 54.q))
            Text(
                when (qrState) {
                    QrState.LOADING -> "공유 링크를 만들고 있어요"
                    QrState.ERROR -> "문서를 만들지 못했어요"
                    QrState.READY -> "휴대폰 카메라로 찍으세요"
                    QrState.OPENED -> "휴대폰에서 문서를 받으세요"
                    QrState.OFFLINE -> "연결되면 바로 QR을 만들게요"
                },
                style = textStyle(if (split) 48 else 60, if (split) 62 else 76, FontWeight.ExtraBold),
            )
            Spacer(Modifier.height(16.q))
            Text(
                if (qrState == QrState.OFFLINE && current != null) {
                    "원본 시각은 저장 순간(${Formats.clock(current.triggeredAtMillis)})으로 고정되어 늦게 보내도 바뀌지 않아요"
                } else {
                    "보험사·경찰에 블랙박스 영상과 함께 내세요"
                },
                style = textStyle(30, 42, color = colors.textSecondary),
            )
            if (qrState == QrState.ERROR) {
                Spacer(Modifier.height(16.q))
                Text(failure?.message ?: "다시 시도해 주세요.", style = textStyle(24, 32, color = colors.textSecondary))
                Spacer(Modifier.height(16.q))
                CustomButton("다시 시도", { current?.let(documentViewModel::createDocument) })
            }
            if (!split) {
                Spacer(Modifier.height(80.q))
                Steps()
                Spacer(Modifier.height(64.q))
                HorizontalDivider(thickness = 2.q, color = colors.line)
                Spacer(Modifier.height(32.q))
                current?.let { r ->
                    Text(r.narrative.getOrNull(1) ?: r.kind.label, style = textStyle(28, 38, FontWeight.Bold), maxLines = 2)
                    Spacer(Modifier.height(8.q))
                    Text("${Formats.date(r.triggeredAtMillis)} · ${Formats.clock(r.triggeredAtMillis)}", style = textStyle(24, 32, color = colors.textSecondary))
                    Spacer(Modifier.height(8.q))
                    Text(if (r.sealed) "원본 지문을 서버에 봉인했어요" else "원본 지문을 아직 서버에 보내지 못했어요", style = textStyle(24, 32, color = colors.textTertiary))
                }
            }
        }

        CustomButton(
            "결과 화면으로", onBack,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (split) 40.q else 748.q, bottom = 64.q)
                .then(if (split) Modifier.padding(end = 40.q).fillMaxWidth() else Modifier),
            icon = AppIcon.BACK,
        )
        HomeButton(
            onClick = onHome,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.q, end = if (split) 40.q else 64.q),
        )
    }
}

private enum class QrState { READY, OPENED, OFFLINE, LOADING, ERROR }

@Composable
private fun QrCard(state: QrState, url: String?, cardSize: Int, qrSize: Int) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(28.q)
    Box(
        Modifier
            .size(cardSize.q)
            .then(if (state == QrState.OFFLINE) Modifier else Modifier.shadow(12.q, shape, ambientColor = Color(0x1A10151B), spotColor = Color(0x1A10151B)).background(Color.White, shape)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state == QrState.OFFLINE -> {
                val unit = 1.q
                Canvas(Modifier.fillMaxSize()) {
                    val u = unit.toPx()
                    drawRoundRect(
                        colors.line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(28 * u),
                        style = Stroke(4 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14 * u, 10 * u))),
                    )
                }
                Text(
                    "통신이 연결되면\n여기에 QR이 나타나요",
                    style = textStyle(28, 40, color = colors.textSecondary), textAlign = TextAlign.Center,
                )
            }
            state == QrState.LOADING || state == QrState.ERROR -> {
                Text(if (state == QrState.LOADING) "공유 링크 준비 중…" else "다시 시도해 주세요", style = textStyle(28, 40, color = colors.textSecondary))
            }
            url != null -> {
                QrCode(url, Modifier.size(qrSize.q))
                if (state == QrState.OPENED) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.88f), shape), contentAlignment = Alignment.Center) {
                        Icon(AppIcon.CHECK, 160.q, colors.recording)
                    }
                }
            }
        }
    }
}

@Composable
private fun Steps() {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(20.q)) {
        listOf("기본 카메라 켜기", "이 QR 비추기", "링크 열고 PDF 받기").forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = textStyle(22, 28, FontWeight.ExtraBold, colors.accent), modifier = Modifier.width(36.q))
                Text(text, style = textStyle(30, 40, FontWeight.Bold))
            }
        }
    }
}
