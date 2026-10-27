package team.momentlog.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import team.momentlog.app.config.AppNavHost
import team.momentlog.app.config.AppViewModels
import team.momentlog.app.config.Routes
import team.momentlog.app.ui.common.AppScaffold

/**
 * 앱 최상위 화면. 공통 틀([AppScaffold]) 안에 화면 이동([AppNavHost])을 넣는다.
 * 주행이 시작되면 어떤 화면에 있든 홈으로 돌아간다.
 *
 * TODO(앱): 저장 중이면 [team.momentlog.app.ui.screens.capture.CaptureOverlay]를 홈 위에 덮기,
 * 정차 + 진술 전 기록이 있으면 진술(3)로 보내기
 */
@Composable
fun MomentlogApp(viewModels: AppViewModels) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isDriving by viewModels.main.isDriving.collectAsState()
    val recorderStatus by viewModels.main.recorderStatus.collectAsState()

    LaunchedEffect(isDriving) {
        if (isDriving && currentRoute != Routes.HOME) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
        }
    }

    AppScaffold(
        currentRoute = currentRoute,
        isDriving = isDriving,
        recorderStatus = recorderStatus,
        onNavigate = { route ->
            navController.navigate(route) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
        },
    ) {
        AppNavHost(navController, viewModels)
    }
}
