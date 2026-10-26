package team.momentlog.app.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import team.momentlog.core.SignalState
import team.momentlog.core.TurnSignal

/**
 * Pleos Vehicle SDK의 조회(State) API로 신호를 받는다. 제어 API는 쓰지 않는다.
 *
 * 근거: Pleos 공식 문서(Vehicle SDK 소개·권한 표), 공개 SDK 소스 `ai.pleos.playground:Vehicle:2.3.2`의
 * 메서드 시그니처와 `@RequiresPermission`. 차량 속도는 Vehicle SDK에 없어 [CarSpeedReader]가 따로 받는다.
 *
 * 구독마다 "등록하고, 해제 방법을 돌려준다"는 형태로 만들어 두고, [stop]에서 등록한 것만 해제한다.
 * 지원하지 않는 차종에서 등록이 실패해도 나머지 신호는 계속 받는다.
 *
 * TODO(앱): 도메인별 check*Capabilities 결과를 로그와 화면에 남기기.
 */
class VehicleSignalClient(context: Context) : SignalSource {

    override var onAirbagDeployed: (() -> Unit)? = null

    private val vehicle = Vehicle(context.applicationContext)
    private val speedReader = CarSpeedReader(context)
    private var initialized = false
    private val unsubscribers = mutableListOf<() -> Unit>()

    override fun start(state: SignalState) {
        if (!initialize()) return
        val signals = state

        subscribe("브레이크") {
            val listener = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) {
                    signals.brake = status
                    received("브레이크", status)
                }
                override fun onFailed(e: Exception) = report("브레이크", e)
            }
            vehicle.getBrake().registerBrakePedalStatus(listener)
            return@subscribe { vehicle.getBrake().unregisterBrakePedalStatus(listener) }
        }

        subscribe("방향지시등") {
            val listener = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                    signals.turn = toTurnSignal(state)
                    received("방향지시등", state)
                }
                override fun onFailed(e: Exception) = report("방향지시등", e)
            }
            vehicle.getTurnSignal().registerTurnSignalState(listener)
            return@subscribe { vehicle.getTurnSignal().unregisterTurnSignalState(listener) }
        }

        subscribe("조향각") {
            val listener = object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) {
                    signals.steeringDeg = angle
                    received("조향각", angle)
                }
                override fun onFailed(e: Exception) = report("조향각", e)
            }
            vehicle.getSteeringWheel().registerSteeringWheelAngle(listener)
            return@subscribe { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(listener) }
        }

        subscribe("하향등") {
            val listener = lightListener("하향등") { on -> signals.lowBeam = on }
            vehicle.getLight().registerLowBeamLightState(listener)
            return@subscribe { vehicle.getLight().unregisterLowBeamLightState(listener) }
        }

        subscribe("상향등") {
            val listener = lightListener("상향등") { on -> signals.highBeam = on }
            vehicle.getLight().registerHighBeamLightState(listener)
            return@subscribe { vehicle.getLight().unregisterHighBeamLightState(listener) }
        }

        subscribe("와이퍼") {
            val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
            val listener = object : WiperStateListener {
                override fun onWiperStateUpdated(state: WiperState?) {
                    signals.wiperOn = state?.let { it == WiperState.ON }
                    received("와이퍼", state)
                }
                override fun onFailed(e: Exception) = report("와이퍼", e)
            }
            vehicle.getWiper().registerWiperState(windshield, listener)
            return@subscribe { vehicle.getWiper().unregisterWiperState(windshield, listener) }
        }

        subscribe("에어백") {
            val driver = SeatArea(SeatValue.ROW_1_LEFT)
            val listener = object : SeatAirbagDeployedListener {
                override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                    received("에어백", deployed)
                    if (deployed == true) onAirbagDeployed?.invoke()
                }
                override fun onFailed(e: Exception) = report("에어백", e)
            }
            vehicle.getSafety().registerSeatAirbagDeployed(driver, listener)
            return@subscribe { vehicle.getSafety().unregisterSeatAirbagDeployed(driver, listener) }
        }

        speedReader.start { kph -> signals.speedKph = kph }
    }

    override fun stop() {
        speedReader.stop()
        unsubscribers.forEach { unsubscribe -> runSdkCall("해제", unsubscribe) }
        unsubscribers.clear()
        if (initialized) {
            runSdkCall("release") { vehicle.release() }
            initialized = false
        }
    }

    private fun initialize(): Boolean {
        if (initialized) return true
        return try {
            vehicle.initialize()
            initialized = true
            true
        } catch (e: Exception) {
            report("initialize", e)
            false
        }
    }

    /** [register]는 등록을 하고 해제 함수를 돌려준다. 등록이 실패하면 기록만 하고 넘어간다. */
    private fun subscribe(name: String, register: () -> (() -> Unit)) {
        try {
            unsubscribers += register()
        } catch (e: Exception) {
            report(name, e)
        }
    }

    private fun lightListener(name: String, onChange: (Boolean?) -> Unit) = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            received(name, state)
            onChange(state?.let { it == LightState.ON })
        }
        override fun onFailed(e: Exception) = report(name, e)
    }

    private fun toTurnSignal(value: TurnSignalValue?): TurnSignal? = when (value) {
        TurnSignalValue.LEFT -> TurnSignal.LEFT
        TurnSignalValue.RIGHT -> TurnSignal.RIGHT
        TurnSignalValue.OFF -> TurnSignal.OFF
        null -> null
    }

    private inline fun runSdkCall(name: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            report(name, e)
        }
    }

    /** 콜백이 실제로 오는지 확인하는 용도. `adb logcat -s Momentlog:D` */
    private fun received(name: String, value: Any?) {
        Log.d(TAG, "차량 신호 [$name] = $value")
    }

    private fun report(name: String, e: Exception) {
        Log.w(TAG, "차량 신호 [$name] 실패: ${e.message}")
    }

    private companion object {
        const val TAG = "Momentlog"
    }
}
