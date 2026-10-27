package team.momentlog.app.config

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import team.momentlog.app.ui.screens.document.DocumentScreen
import team.momentlog.app.ui.screens.home.HomeScreen
import team.momentlog.app.ui.screens.monthly.MonthlyScreen
import team.momentlog.app.ui.screens.record.RecordDetailScreen
import team.momentlog.app.ui.screens.record.RecordListScreen
import team.momentlog.app.ui.screens.settings.SettingsScreen
import team.momentlog.app.ui.screens.statement.StatementScreen
import team.momentlog.app.ui.screens.use_case.DocumentViewModel
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.use_case.StatementViewModel
import team.momentlog.app.ui.screens.widget.motion.EaseOut

/** 화면 경로. */
object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val SETTINGS = "settings"

    const val ARG_RECORD_ID = "recordId"
    const val ARG_SUBSCRIBE = "subscribe"
    const val RECORD_DETAIL = "records/{$ARG_RECORD_ID}"
    const val STATEMENT = "records/{$ARG_RECORD_ID}/statement"
    const val DOCUMENT = "records/{$ARG_RECORD_ID}/document"
    const val MONTHLY = "monthly?$ARG_SUBSCRIBE={$ARG_SUBSCRIBE}"

    fun recordDetail(recordId: String) = "records/$recordId"
    fun statement(recordId: String) = "records/$recordId/statement"
    fun document(recordId: String) = "records/$recordId/document"
    fun monthly(subscribe: Boolean) = "monthly?$ARG_SUBSCRIBE=$subscribe"
}

/** ViewModel들은 Activity 범위로 만들어 화면끼리 공유한다. */
class AppViewModels(
    val main: MainViewModel,
    val record: RecordViewModel,
    val statement: StatementViewModel,
    val document: DocumentViewModel,
)

@Composable
fun AppNavHost(navController: NavHostController, viewModels: AppViewModels) {
    val recordIdArgument = listOf(navArgument(Routes.ARG_RECORD_ID) { type = NavType.StringType })
    val toHome: () -> Unit = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
    val toRecords: () -> Unit = {
        if (!navController.popBackStack(Routes.RECORDS, false)) {
            navController.navigate(Routes.RECORDS) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(viewModels.main, onOpenRecords = {
                viewModels.record.resetListPosition()
                toRecords()
            })
        }
        composable(Routes.RECORDS) {
            RecordListScreen(
                viewModels.record,
                onOpen = { navController.navigate(Routes.recordDetail(it.id)) },
                onStatement = { navController.navigate(Routes.statement(it.id)) },
                onSendToPhone = { navController.navigate(Routes.document(it.id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onHome = toHome,
            )
        }
        composable(
            Routes.RECORD_DETAIL,
            arguments = recordIdArgument,
            enterTransition = { fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = EaseOut)) { it / 16 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(280, easing = EaseOut)) { it / 16 } },
        ) { entry ->
            val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
            RecordDetailScreen(
                recordId = recordId,
                recordViewModel = viewModels.record,
                onSendToPhone = { navController.navigate(Routes.document(recordId)) },
                onOpenRecords = toRecords,
                onStatement = { navController.navigate(Routes.statement(recordId)) },
            )
        }
        composable(Routes.STATEMENT, arguments = recordIdArgument) { entry ->
            val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
            val records by viewModels.record.records.collectAsState()
            StatementScreen(
                record = records.firstOrNull { it.id == recordId },
                recordId = recordId,
                statementViewModel = viewModels.statement,
                onSaved = {
                    viewModels.record.loadRecords()
                    navController.navigate(Routes.recordDetail(recordId)) { popUpTo(Routes.HOME) }
                },
                onOpenRecords = toRecords,
                onClose = toHome,
            )
        }
        composable(Routes.DOCUMENT, arguments = recordIdArgument) { entry ->
            val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
            DocumentScreen(recordId, viewModels.record, viewModels.document, onBack = { navController.popBackStack() }, onHome = toHome)
        }
        composable(
            Routes.MONTHLY,
            arguments = listOf(navArgument(Routes.ARG_SUBSCRIBE) { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            MonthlyScreen(
                subscribe = entry.arguments?.getBoolean(Routes.ARG_SUBSCRIBE) == true,
                mainViewModel = viewModels.main,
                recordViewModel = viewModels.record,
                onConfirm = {
                    viewModels.main.dismissMonthly()
                    toHome()
                },
                onOpenRecords = {
                    viewModels.main.dismissMonthly()
                    toRecords()
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModels.main, onBack = { navController.popBackStack() })
        }
    }
}
