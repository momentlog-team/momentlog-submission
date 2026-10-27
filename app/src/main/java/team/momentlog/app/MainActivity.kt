package team.momentlog.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import team.momentlog.app.config.AppViewModels
import team.momentlog.app.config.theme.MomentlogTheme
import team.momentlog.app.ui.MomentlogApp
import team.momentlog.app.ui.screens.use_case.DocumentViewModel
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.use_case.StatementViewModel

/** 진입점. 권한을 요청하고 ViewModel을 만들어 [MomentlogApp]에 넘긴다. */
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val recordViewModel: RecordViewModel by viewModels()
    private val statementViewModel: StatementViewModel by viewModels()
    private val documentViewModel: DocumentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(
            arrayOf("android.car.permission.CAR_SPEED", Manifest.permission.POST_NOTIFICATIONS),
            0,
        )
        val viewModels = AppViewModels(mainViewModel, recordViewModel, statementViewModel, documentViewModel)
        setContent { MomentlogTheme { MomentlogApp(viewModels) } }
    }
}
