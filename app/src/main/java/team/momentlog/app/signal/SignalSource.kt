package team.momentlog.app.signal

import team.momentlog.core.SignalState

/**
 * 차량 신호를 [SignalState]에 채워 넣는 출처.
 * 실차·에뮬레이터는 [VehicleSignalClient], 시연은 [ReplaySource]를 쓴다. 기록 로직은 둘을 구분하지 않는다.
 */
interface SignalSource {
    fun start(state: SignalState)
    fun stop()

    /** 에어백 전개처럼 즉시 기록해야 하는 신호가 오면 호출된다. */
    var onAirbagDeployed: (() -> Unit)?
}
