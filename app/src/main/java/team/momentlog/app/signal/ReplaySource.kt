package team.momentlog.app.signal

import android.os.Handler
import android.os.Looper
import team.momentlog.core.SignalState
import team.momentlog.core.TurnSignal

/**
 * 시연용 재생. 기획서 시연 시나리오(교차로 우회전 중 후방 추돌)를 매번 같은 값으로 보여 주려고 앱 안에서 재생한다.
 * Virtual Console로도 속도를 바꿀 수 있지만(10/26 실측) 손으로는 같은 장면을 반복하기 어렵다.
 *
 * 장면(충돌 = [CRASH_AT_MS]):
 * - 0~6초 정차에서 출발해 32km/h까지 가속, 그 뒤 정속
 * - 충돌 6.2초 전 우측 방향지시등, 4.1초 전부터 브레이크로 12km/h까지 서서히 감속
 * - 충돌: 브레이크를 밟은 채 12→15km/h로 밀림 → core 충격 감지(뒤에서 밀림)
 * - 이후 3.5초에 걸쳐 멈춤
 * 감속은 급정지 기준(1.5초 안 8km/h 이상)보다 완만하게 둬서 충격 한 건만 잡히게 한다.
 *
 * 화면에는 "시연 데이터" 표시를 반드시 띄운다(심사에서 실제 신호로 오해하지 않게).
 */
class ReplaySource : SignalSource {
    override var onAirbagDeployed: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var state: SignalState? = null

    private val tick = object : Runnable {
        override fun run() {
            val s = state ?: return
            val t = System.currentTimeMillis() - startedAt
            val toCrash = CRASH_AT_MS - t
            s.speedKph = speedAt(t, toCrash)
            s.turn = if (toCrash <= 6_200 && toCrash > -3_000) TurnSignal.RIGHT else TurnSignal.OFF
            s.brake = toCrash <= 4_100
            s.steeringDeg = 0f
            s.lowBeam = false
            s.wiperOn = false
            if (t < CRASH_AT_MS + 15_000) handler.postDelayed(this, 50)
        }
    }

    private fun speedAt(t: Long, toCrash: Long): Float = when {
        t < DEPART_MILLIS -> CRUISE_KPH * smooth(t / DEPART_MILLIS.toFloat())
        toCrash > 4_100 -> CRUISE_KPH
        toCrash > 0 -> CRUISE_KPH - (4_100 - toCrash) / 4_100f * (CRUISE_KPH - BEFORE_HIT_KPH)
        -toCrash < STOP_MILLIS -> PUSHED_KPH * (1f - -toCrash / STOP_MILLIS.toFloat())
        else -> 0f
    }

    /** 출발 가속을 부드럽게(처음과 끝이 완만한 곡선). */
    private fun smooth(x: Float) = x * x * (3 - 2 * x)

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
        const val CRASH_AT_MS = 26_000L
        private const val DEPART_MILLIS = 6_000L
        private const val STOP_MILLIS = 3_500L
        private const val CRUISE_KPH = 32f
        private const val BEFORE_HIT_KPH = 12f
        private const val PUSHED_KPH = 15f
    }
}
