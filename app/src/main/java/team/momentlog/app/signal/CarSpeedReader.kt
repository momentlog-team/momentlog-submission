package team.momentlog.app.signal

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 차량 속도. Pleos 공식 FAQ("클러스터 속도 조회 방법")가 안내하는 대로 Android Automotive 표준
 * `CarPropertyManager`의 `PERF_VEHICLE_SPEED`를 읽는다. 사용법은 Android 공식 문서(VehiclePropertyIds)를 따른다.
 * - 단위: m/s. 후진은 음수일 수 있다.
 * - 권한: `android.car.permission.CAR_SPEED` (런타임 허용 필요)
 */
class CarSpeedReader(context: Context) {

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null

    fun start(onSpeedKph: (Float) -> Unit) {
        try {
            val connected = Car.createCar(appContext)
            val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val metersPerSecond = value.value as? Float ?: return
                    Log.d(TAG, "차량 속도 = $metersPerSecond m/s")
                    onSpeedKph(kotlin.math.abs(metersPerSecond) * 3.6f)
                }
                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    Log.w(TAG, "속도 수신 오류 property=$propertyId area=$areaId")
                }
            }
            manager.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
            car = connected
            callback = cb
        } catch (e: Exception) {
            Log.w(TAG, "속도 구독 실패: ${e.message}")
        }
    }

    fun stop() {
        try {
            val cb = callback
            if (cb != null) (car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.unregisterCallback(cb)
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "속도 해제 실패: ${e.message}")
        } finally {
            callback = null
            car = null
        }
    }

    private companion object {
        const val TAG = "Momentlog"
    }
}
