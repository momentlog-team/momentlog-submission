package team.momentlog.core

import java.util.UUID

/**
 * 저장 구간. 기준 시점 앞 [preMillis]를 남기고, 뒤로는 [postMillis]를 남긴다.
 * [afterStop]이면 뒤 구간을 "차가 멈춘 뒤"부터 센다. 멈추지 않으면 기준 시점 뒤 [MAX_WAIT_FOR_STOP_MILLIS]에서 끝낸다.
 */
data class CaptureWindow(val preMillis: Long, val postMillis: Long, val afterStop: Boolean) {
    companion object {
        const val MAX_WAIT_FOR_STOP_MILLIS = 60_000L

        /** 이 속도 미만이면 멈춘 것으로 본다. */
        const val STOPPED_KPH = 1f
    }
}

/** 기록이 저장된 이유. */
enum class Trigger(val label: String, val window: CaptureWindow) {
    HARD_STOP("급정지", CaptureWindow(30_000, 10_000, afterStop = true)),
    VOICE("기록 요청", CaptureWindow(180_000, 30_000, afterStop = false)),
    AIRBAG("에어백 전개", CaptureWindow(30_000, 10_000, afterStop = true)),
}

/** 기록 안의 감지 시점 하나. 첫 감지(기준 시점)와 이어 붙은 감지, 합친 앞선 기록의 감지가 모두 들어간다. */
data class IncidentEvent(val trigger: Trigger, val atMillis: Long)

/**
 * 저장된 한 건의 기록.
 * [events]는 시각 순서이고 기준 시점 이벤트를 반드시 포함한다. [mergedRecordIds]는 이 기록에 합쳐져 지워도 되는 앞선 기록.
 */
data class IncidentRecord(
    val id: String,
    val trigger: Trigger,
    val triggerAtMillis: Long,
    val snapshots: List<Snapshot>,
    val events: List<IncidentEvent> = listOf(IncidentEvent(trigger, triggerAtMillis)),
    val mergedRecordIds: List<String> = emptyList(),
)

/**
 * 스냅샷을 계속 받으면서 급정지·음성 요청·에어백 신호가 오면 기록을 만든다.
 * 기준 시점 뒤 저장 구간이 끝나면 [onSnapshot]이 완성된 기록을 돌려준다.
 *
 * - 버퍼는 가장 긴 저장 구간(3분)만큼 들고 있고, 기록을 만든 뒤에도 비우지 않는다(이어지는 "기록해"가 앞 구간을 쓸 수 있게).
 * - 저장하는 동안 다시 감지되면 새 기록을 만들지 않고 같은 기록에 이어 붙이고, 저장 구간을 그 감지 기준으로 늘린다.
 * - "기록해"의 직전 3분 안에 앞서 완성된 기록이 있으면 그 감지를 합치고 [IncidentRecord.mergedRecordIds]로 알린다.
 */
class IncidentRecorder(
    private val detector: HardStopDetector = HardStopDetector(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** 직전 구간 + 직후 구간을 다 담을 수 있어야 기록이 완성될 때 앞부분이 밀려나지 않는다. */
    private val bufferMillis = Trigger.entries.maxOf { it.window.preMillis } +
        maxOf(Trigger.entries.maxOf { it.window.postMillis }, CaptureWindow.MAX_WAIT_FOR_STOP_MILLIS) + 10_000
    private val buffer = SnapshotBuffer(bufferMillis)
    private var pending: Pending? = null
    private val completed = ArrayDeque<IncidentRecord>()

    private class Pending(val trigger: Trigger, val atMillis: Long, merged: List<IncidentRecord>) {
        val startAtMillis = atMillis - trigger.window.preMillis
        /** 정해진 끝 시각. 멈추기를 기다리는 동안은 [waitingSince]부터 [CaptureWindow.MAX_WAIT_FOR_STOP_MILLIS]가 상한. */
        var endAtMillis = atMillis
        var waitingSince: Long? = null
        val events = (merged.flatMap { it.events } + IncidentEvent(trigger, atMillis)).sortedBy { it.atMillis }.toMutableList()
        val mergedIds = merged.map { it.id }
    }

    val isCapturing: Boolean get() = pending != null

    /** 저장 중인 기록의 감지 시점들(시각 순서). 저장 중이 아니면 빈 목록. */
    val pendingEvents: List<IncidentEvent> get() = pending?.events?.toList().orEmpty()

    /**
     * 음성 "기록해"나 에어백 신호처럼 밖에서 기준 시점을 정할 때 부른다.
     * [postMillis]를 주면 계기 기본값 대신 쓴다(정차 중 사후 저장은 0).
     * 이미 저장 중이면 같은 기록에 이어 붙인다.
     */
    fun trigger(trigger: Trigger, atMillis: Long, postMillis: Long = trigger.window.postMillis) {
        val capturing = pending
        if (capturing != null) {
            append(capturing, trigger, atMillis, postMillis)
            return
        }
        val merged = if (trigger == Trigger.VOICE) {
            completed.filter { it.events.any { e -> e.atMillis >= atMillis - trigger.window.preMillis } }
        } else {
            emptyList()
        }
        pending = Pending(trigger, atMillis, merged).also { schedule(it, trigger, atMillis, postMillis) }
    }

    /** 스냅샷을 넣는다. 기록이 완성되면 그 기록을, 아니면 null을 돌려준다. */
    fun onSnapshot(snapshot: Snapshot): IncidentRecord? {
        buffer.add(snapshot)
        val hardStop = detector.feed(snapshot)
        forgetOld(snapshot.atMillis)

        val capturing = pending
        if (capturing == null) {
            if (hardStop) trigger(Trigger.HARD_STOP, snapshot.atMillis)
            return completeIfDue(snapshot.atMillis)
        }
        if (hardStop) append(capturing, Trigger.HARD_STOP, snapshot.atMillis, Trigger.HARD_STOP.window.postMillis)
        return completeIfDue(snapshot.atMillis)
    }

    private fun append(capturing: Pending, trigger: Trigger, atMillis: Long, postMillis: Long) {
        capturing.events += IncidentEvent(trigger, atMillis)
        schedule(capturing, trigger, atMillis, postMillis)
    }

    /** 계기에 맞춰 끝 시각을 늦춘다(앞당기지는 않는다). 멈춘 뒤 구간이면 멈출 때까지 기다린다. */
    private fun schedule(capturing: Pending, trigger: Trigger, atMillis: Long, postMillis: Long) {
        if (trigger.window.afterStop && postMillis > 0) {
            capturing.waitingSince = atMillis
            capturing.endAtMillis = maxOf(capturing.endAtMillis, atMillis)
        } else {
            capturing.endAtMillis = maxOf(capturing.endAtMillis, atMillis + postMillis)
        }
    }

    private fun completeIfDue(nowMillis: Long): IncidentRecord? {
        val capturing = pending ?: return null
        val waitingSince = capturing.waitingSince
        if (waitingSince != null) {
            val stopped = (buffer.latest()?.speedKph ?: 0f) < CaptureWindow.STOPPED_KPH
            val gaveUp = nowMillis - waitingSince >= CaptureWindow.MAX_WAIT_FOR_STOP_MILLIS
            if (!stopped && !gaveUp) return null
            capturing.waitingSince = null
            capturing.endAtMillis = maxOf(capturing.endAtMillis, if (stopped) nowMillis + capturing.trigger.window.postMillis else nowMillis)
        }
        if (nowMillis < capturing.endAtMillis) return null

        pending = null
        val record = IncidentRecord(
            id = newId(),
            trigger = capturing.trigger,
            triggerAtMillis = capturing.atMillis,
            snapshots = buffer.toList().filter { it.atMillis in capturing.startAtMillis..capturing.endAtMillis },
            events = capturing.events.toList(),
            mergedRecordIds = capturing.mergedIds,
        )
        completed.removeAll { it.id in capturing.mergedIds }
        completed.addLast(record)
        return record
    }

    /** 합칠 수 있는 앞선 기록은 "기록해" 직전 구간 안에 감지가 있는 것뿐이다. */
    private fun forgetOld(nowMillis: Long) {
        while (completed.isNotEmpty() && completed.first().events.last().atMillis < nowMillis - bufferMillis) completed.removeFirst()
    }
}
