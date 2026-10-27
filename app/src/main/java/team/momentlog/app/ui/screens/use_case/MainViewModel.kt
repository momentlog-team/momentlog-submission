package team.momentlog.app.ui.screens.use_case

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import team.momentlog.app.Constant
import team.momentlog.app.RecorderService
import team.momentlog.app.core.vehicle.DrivingStateMonitor
import team.momentlog.core.Snapshot
import java.util.Calendar

/**
 * 앱 전체 상태: 주행 중인지, 기록 서비스 상태, 낮/밤 테마, 진행 중인 저장.
 */
class MainViewModel(application: Application) : BaseViewModel(application) {

    private val drivingMonitor = DrivingStateMonitor(application).also { it.start() }

    val recorderStatus: StateFlow<RecorderService.Status> = RecorderService.status
    val live: StateFlow<Snapshot?> = RecorderService.live
    val capture: StateFlow<RecorderService.Capture?> = RecorderService.capture

    /**
     * 시스템이 주의 분산 최소화를 요구하거나 속도가 [Constant.RESUME_SPEED_KPH] 이상이면 주행 중.
     * TODO(앱): Vehicle SDK에서 기어(P/D/R) 조회가 되면 "속도 0 또는 기어 P = 정차" 기준으로 바꾸기
     */
    val isDriving: StateFlow<Boolean> = combine(drivingMonitor.isDriving, live) { restricted, snapshot ->
        restricted || (snapshot?.speedKph ?: 0f) >= Constant.RESUME_SPEED_KPH
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isNight: StateFlow<Boolean> = live.map { it?.lowBeam == true || it?.highBeam == true }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 차종이 지원하지 않거나 값을 못 받은 신호. 서비스가 꺼져 있으면 빈 목록. */
    val unsupportedSignals: StateFlow<List<String>> = live.map { s ->
        if (s == null) emptyList() else buildList {
            if (s.speedKph == null) add("속도")
            if (s.brake == null) add("브레이크")
            if (s.turn == null) add("방향지시등")
            if (s.steeringDeg == null) add("조향각")
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val prefs = application.getSharedPreferences("main", Context.MODE_PRIVATE)
    private val _showMonthly = MutableStateFlow(false)
    /** 월간 점검 안내를 띄울지. 매월 첫 정차에 한 번. */
    val showMonthly: StateFlow<Boolean> = _showMonthly

    fun startRecording(replay: Boolean) {
        RecorderService.start(getApplication(), replay)
    }

    fun stopRecording() {
        RecorderService.stop(getApplication())
    }

    fun recordNow() {
        RecorderService.recordNow(getApplication())
    }

    fun dismissCapture() {
        RecorderService.clearCapture()
    }

    fun checkMonthly() {
        val month = Calendar.getInstance().let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) + 1 }
        if (prefs.getInt(KEY_MONTHLY_SHOWN, 0) == month) return
        prefs.edit().putInt(KEY_MONTHLY_SHOWN, month).apply()
        _showMonthly.value = true
    }

    fun dismissMonthly() {
        _showMonthly.value = false
    }

    override fun onCleared() {
        drivingMonitor.stop()
        super.onCleared()
    }

    private companion object {
        const val KEY_MONTHLY_SHOWN = "monthlyShown"
    }
}
