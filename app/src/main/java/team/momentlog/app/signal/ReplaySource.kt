package team.momentlog.app.signal

import android.os.Handler
import android.os.Looper
import team.momentlog.core.SignalState
import team.momentlog.core.TurnSignal

/**
 * 시연용 재생. Pleos 공식 Virtual Console 가이드의 조작 항목에는 차량 속도가 없어서,
 * 교차로 우회전 중 후방 추돌 장면을 앱 안에서 재생한다.
 *
 * 화면에는 "시연 데이터" 표시를 반드시 띄운다(심사에서 실제 신호로 오해하지 않게).
 * TODO(앱): 방향지시등·브레이크는 Virtual Console 값으로도 덮어쓸 수 있게 VehicleSignalClient와 함께 돌리기.
 */
class ReplaySource : SignalSource {
    override var onAirbagDeployed: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var state: SignalState? = null

    /** 재생 시작 후 [CRASH_AT_MS]에 추돌. 그 전에 지시등·브레이크를 단계적으로 켠다. */
    private val tick = object : Runnable {
        override fun run() {
            val s = state ?: return
            val t = System.currentTimeMillis() - startedAt
            val toCrash = CRASH_AT_MS - t
            s.speedKph = when {
                toCrash > 4_100 -> 32f
                toCrash > 1_000 -> 32f - (4_100 - toCrash) / 3_100f * 18f
                toCrash > 0 -> 14f
                else -> 0f
            }
            s.turn = if (toCrash <= 6_200 && toCrash > -3_000) TurnSignal.RIGHT else TurnSignal.OFF
            s.brake = toCrash <= 4_100
            s.steeringDeg = if (toCrash in 1..1_500) 12f else 0f
            s.lowBeam = false
            s.wiperOn = false
            if (t < CRASH_AT_MS + 15_000) handler.postDelayed(this, 50)
        }
    }

    override fun start(state: SignalState) {
        this.state = state
        startedAt = System.currentTimeMillis()
        handler.post(tick)
    }

    override fun stop() {
        handler.removeCallbacks(tick)
        state = null
    }

    companion object {
        const val CRASH_AT_MS = 20_000L
    }
}
