package team.momentlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import team.momentlog.app.RecorderService
import team.momentlog.app.config.AppNavHost
import team.momentlog.app.config.AppViewModels
import team.momentlog.app.config.Routes
import team.momentlog.app.config.theme.LocalAppColors
import team.momentlog.app.config.theme.MomentlogTheme
import team.momentlog.app.config.theme.q
import team.momentlog.app.config.theme.textStyle
import team.momentlog.app.ui.models.RecordKind
import team.momentlog.app.ui.screens.capture.CaptureOverlay
import team.momentlog.app.ui.screens.resume.ResumeNotice
import team.momentlog.app.ui.screens.resume.ResumeReason
import team.momentlog.app.ui.screens.use_case.StatementViewModel

/**
 * 앱 최상위 화면. 차량 시스템의 주·야간 테마를 따른다.
 * 저장이 시작되면 저장 알림을 덮고, 저장이 끝난 뒤 정차 중이면 진술로 간다.
 * 주행이 다시 시작되면 대기 화면으로 돌아간다.
 */
@Composable
fun MomentlogApp(viewModels: AppViewModels) {
    val main = viewModels.main
    val isDriving by main.isDriving.collectAsState()
    val capture by main.capture.collectAsState()
    val recorderStatus by main.recorderStatus.collectAsState()
    val showMonthly by main.showMonthly.collectAsState()
    val statementStep by viewModels.statement.step.collectAsState()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var resumeReason by remember { mutableStateOf<ResumeReason?>(null) }
    /** 정차 중 저장에서 기록이 완성되기 전에 누른 버튼. 완성되면 실행한다. */
    var afterStopAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    LaunchedEffect(isDriving) {
        if (isDriving && currentRoute != null && currentRoute != Routes.HOME) {
            resumeReason = if (currentRoute == Routes.STATEMENT && statementStep in listOf(StatementViewModel.Step.LISTENING, StatementViewModel.Step.EDITING)) {
                viewModels.statement.saveInterrupted()
                ResumeReason.STATEMENT
            } else {
                ResumeReason.SCREEN
            }
            navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
        }
        if (!isDriving) main.checkMonthly()
    }

    LaunchedEffect(showMonthly, isDriving, capture) {
        if (showMonthly && !isDriving && capture == null && currentRoute == Routes.HOME) {
            navController.navigate(Routes.monthly(subscribe = false))
        }
    }

    val finishedId = capture?.recordId
    LaunchedEffect(finishedId, isDriving, afterStopAction) {
        val done = capture ?: return@LaunchedEffect
        val id = done.recordId ?: return@LaunchedEffect
        viewModels.record.loadRecords()
        val kind = RecordKind.of(done.trigger, done.parked)
        if (kind == RecordKind.AFTER_STOP) {
            afterStopAction?.let { action ->
                afterStopAction = null
                main.dismissCapture()
                action(id)
            }
            return@LaunchedEffect
        }
        if (isDriving) return@LaunchedEffect
        main.dismissCapture()
        navController.navigate(Routes.statement(id)) { popUpTo(Routes.HOME) }
    }

    MomentlogTheme {
        Box(Modifier.fillMaxSize()) {
            AppNavHost(navController, viewModels)

            capture?.let { c ->
                val kind = RecordKind.of(c.trigger, c.parked)
                CaptureOverlay(
                    kind = kind,
                    atMillis = c.atMillis,
                    parked = !isDriving,
                    events = c.events,
                    onSpeak = {
                        afterStopAction = { id ->
                            navController.navigate(Routes.statement(id)) { popUpTo(Routes.HOME) }
                            viewModels.statement.start(id)
                            viewModels.statement.speak()
                        }
                    },
                    onLater = {
                        afterStopAction = { id ->
                            viewModels.statement.start(id)
                            viewModels.statement.later()
                        }
                    },
                )
            }

            resumeReason?.let { reason ->
                ResumeNotice(reason, onFinished = { resumeReason = null }, modifier = Modifier.align(Alignment.TopCenter))
            }

            // 시연 재생 중에는 실제 신호로 오해하지 않게 항상 표시한다.
            if (recorderStatus == RecorderService.Status.REPLAYING) {
                val colors = LocalAppColors.current
                Text(
                    "시연 데이터",
                    style = textStyle(20, 24, FontWeight.Bold, colors.bg),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.q)
                        .background(colors.danger, CircleShape)
                        .padding(horizontal = 18.q, vertical = 8.q),
                )
            }
        }
    }
}
