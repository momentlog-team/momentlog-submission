package team.momentlog.core

import java.util.UUID

/** 기록이 저장된 이유. */
enum class Trigger(val label: String) {
    HARD_STOP("급정지"),
    VOICE("기록 요청"),
    AIRBAG("에어백 전개"),
}

/** 저장된 한 건의 기록. 기준 시점 앞 30초와 뒤 10초가 들어 있다. */
data class IncidentRecord(
    val id: String,
    val trigger: Trigger,
    val triggerAtMillis: Long,
    val snapshots: List<Snapshot>,
)

/**
 * 스냅샷을 계속 받으면서 급정지·음성 요청·에어백 신호가 오면 기록을 만든다.
 * 기준 시점 뒤 [postMillis]까지 모은 다음 [onSnapshot]이 완성된 기록을 돌려준다.
 */
class IncidentRecorder(
    preMillis: Long = 30_000,
    private val postMillis: Long = 10_000,
    private val detector: HardStopDetector = HardStopDetector(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val buffer = SnapshotBuffer(preMillis)
    private var pending: Pending? = null

    private class Pending(val trigger: Trigger, val atMillis: Long, val before: List<Snapshot>) {
        val after = mutableListOf<Snapshot>()
    }

    val isCapturing: Boolean get() = pending != null

    /** 음성 "기록해"나 에어백 신호처럼 밖에서 기준 시점을 정할 때 부른다. 이미 모으는 중이면 무시한다. */
    fun trigger(trigger: Trigger, atMillis: Long) {
        if (pending != null) return
        pending = Pending(trigger, atMillis, buffer.toList())
    }

    /** 스냅샷을 넣는다. 기록이 완성되면 그 기록을, 아니면 null을 돌려준다. */
    fun onSnapshot(snapshot: Snapshot): IncidentRecord? {
        val capturing = pending
        if (capturing == null) {
            buffer.add(snapshot)
            if (detector.feed(snapshot)) pending = Pending(Trigger.HARD_STOP, snapshot.atMillis, buffer.toList())
            return null
        }

        detector.feed(snapshot)
        if (snapshot.atMillis > capturing.atMillis) capturing.after += snapshot
        if (snapshot.atMillis - capturing.atMillis < postMillis) return null

        pending = null
        buffer.clear()
        buffer.add(snapshot)
        return IncidentRecord(
            id = newId(),
            trigger = capturing.trigger,
            triggerAtMillis = capturing.atMillis,
            snapshots = capturing.before + capturing.after,
        )
    }
}
