package team.momentlog.app.config

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import team.momentlog.app.ui.screens.document.DocumentScreen
import team.momentlog.app.ui.screens.home.HomeScreen
import team.momentlog.app.ui.screens.record.RecordDetailScreen
import team.momentlog.app.ui.screens.record.RecordListScreen
import team.momentlog.app.ui.screens.settings.SettingsScreen
import team.momentlog.app.ui.screens.statement.StatementScreen
import team.momentlog.app.ui.screens.use_case.DocumentViewModel
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.use_case.StatementViewModel

/** 화면 경로. */
object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val SETTINGS = "settings"

    const val ARG_RECORD_ID = "recordId"
    const val RECORD_DETAIL = "records/{$ARG_RECORD_ID}"
    const val STATEMENT = "records/{$ARG_RECORD_ID}/statement"
    const val DOCUMENT = "records/{$ARG_RECORD_ID}/document"

    fun recordDetail(recordId: String) = "records/$recordId"
    fun statement(recordId: String) = "records/$recordId/statement"
    fun document(recordId: String) = "records/$recordId/document"
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

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(viewModels.main)
        }
        composable(Routes.RECORDS) {
            RecordListScreen(viewModels.record, onOpen = { navController.navigate(Routes.recordDetail(it.id)) })
        }
        composable(Routes.RECORD_DETAIL, arguments = recordIdArgument) { entry ->
            val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
            RecordDetailScreen(
                recordId = recordId,
                recordViewModel = viewModels.record,
                onMakeDocument = { navController.navigate(Routes.document(recordId)) },
                onStatement = { navController.navigate(Routes.statement(recordId)) },
            )
        }
        composable(Routes.STATEMENT, arguments = recordIdArgument) { entry ->
            val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
            StatementScreen(
                recordId = recordId,
                statementViewModel = viewModels.statement,
                onLater = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.recordDetail(recordId)) {
                        popUpTo(Routes.RECORDS)
                    }
                },
            )
        }
        composable(Routes.DOCUMENT, arguments = recordIdArgument) { entry ->
            val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
            DocumentScreen(recordId, viewModels.record, viewModels.document)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModels.main)
        }
    }
}
