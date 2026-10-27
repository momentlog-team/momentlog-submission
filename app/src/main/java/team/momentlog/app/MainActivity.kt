package team.momentlog.app

import android.Manifest
import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toBitmap
import team.momentlog.app.config.AppViewModels
import team.momentlog.app.ui.MomentlogApp
import team.momentlog.app.ui.screens.use_case.DocumentViewModel
import team.momentlog.app.ui.screens.use_case.MainViewModel
import team.momentlog.app.ui.screens.use_case.RecordViewModel
import team.momentlog.app.ui.screens.use_case.StatementViewModel

/** 진입점. 권한을 요청하고, 기록이 꺼져 있으면 켜고, ViewModel을 만들어 [MomentlogApp]에 넘긴다. */
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val recordViewModel: RecordViewModel by viewModels()
    private val statementViewModel: StatementViewModel by viewModels()
    private val documentViewModel: DocumentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 차량 독에서도 런처와 같은 아이콘을 쓰도록 실행 작업에 직접 지정한다.
        @Suppress("DEPRECATION")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.app_name),
                requireNotNull(getDrawable(R.mipmap.ic_launcher)).toBitmap(192, 192),
            ),
        )
        requestPermissions(
            arrayOf("android.car.permission.CAR_SPEED", Manifest.permission.POST_NOTIFICATIONS),
            0,
        )
        if (RecorderService.status.value == RecorderService.Status.STOPPED) mainViewModel.startRecording(replay = false)
        val viewModels = AppViewModels(mainViewModel, recordViewModel, statementViewModel, documentViewModel)
        setContent { MomentlogApp(viewModels) }
    }
}
