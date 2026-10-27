package team.momentlog.app.ui.screens.use_case

import android.app.Application
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import team.momentlog.app.Constant
import team.momentlog.app.RecorderService
import team.momentlog.app.core.vehicle.DrivingStateMonitor

/**
 * 앱 전체 상태: 주행 중인지, 기록 서비스가 켜져 있는지.
 * 화면 전환(주행 시작 → 홈으로)과 상단 바가 이 값을 본다.
 */
class MainViewModel(application: Application) : BaseViewModel(application) {

    private val drivingMonitor = DrivingStateMonitor(application).also { it.start() }

    val isDriving: StateFlow<Boolean> = drivingMonitor.isDriving
    val recorderStatus: StateFlow<RecorderService.Status> = RecorderService.status

    // TODO(앱): RecorderService에 실시간 신호(속도·브레이크·지시등·핸들)와 저장 진행률 StateFlow를 추가하고 여기서 내보내기

    fun startRecording(replay: Boolean) {
        RecorderService.start(getApplication(), replay)
    }

    fun stopRecording() {
        RecorderService.stop(getApplication())
    }

    /** 홈의 "지금 기록" 버튼. */
    fun recordNow() {
        // TODO(앱): RecorderService에 수동 트리거(Trigger.VOICE) 인텐트를 추가하고 여기서 보내기
        Log.d(Constant.LOG_TAG, "지금 기록 요청")
    }

    override fun onCleared() {
        drivingMonitor.stop()
        super.onCleared()
    }
}
