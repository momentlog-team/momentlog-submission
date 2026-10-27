package team.momentlog.app.core.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import team.momentlog.app.Constant

/**
 * 주행 중인지 알려 준다. Android Automotive `CarUxRestrictionsManager`가 "주의 분산 최소화가 필요하다"고
 * 하면 주행 중으로 본다. 주행 중에는 홈 화면만 보여 주고 나머지 화면은 닫는다.
 *
 * TODO(앱): 에뮬레이터에서 기어 D일 때 이 값이 true로 바뀌는지 확인
 */
class DrivingStateMonitor(context: Context) {

    private val appContext = context.applicationContext
    private var car: Car? = null

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving

    fun start() {
        if (car != null) return
        try {
            val connected = Car.createCar(appContext)
            val manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _isDriving.value = manager.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            manager.registerListener { restrictions ->
                _isDriving.value = restrictions.isRequiresDistractionOptimization
            }
            car = connected
        } catch (e: Exception) {
            Log.w(Constant.LOG_TAG, "주행 상태 구독 실패: ${e.message}")
        }
    }

    fun stop() {
        try {
            (car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.unregisterListener()
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(Constant.LOG_TAG, "주행 상태 해제 실패: ${e.message}")
        } finally {
            car = null
        }
    }
}
