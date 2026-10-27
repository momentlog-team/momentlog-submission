package team.momentlog.core

import java.util.Locale
import kotlin.math.abs

/**
 * 기록을 사람이 읽는 경위 문장으로 바꾼다.
 * 영상처럼 프레임을 넘겨 보지 않아도 "언제 깜빡이를 켰고, 언제부터 브레이크를 밟았는지"가 보이게 하는 것이 목적이다.
 *
 * 판단이 애매하면 단정하지 않고 사실만 적는다. 값이 없으면 "정보 없음"으로 남긴다.
 */
class IncidentNarrator(
    /** 조향각에서 왼쪽이 음수인지. [Snapshot]은 왼쪽 음수로 저장하므로 true. */
    private val steeringLeftIsNegative: Boolean = true,
    /** 이 각도보다 작으면 핸들은 정면으로 본다. */
    private val straightThresholdDeg: Float = 5f,
) {
    fun narrate(record: IncidentRecord): List<String> {
        val label = record.trigger.label
        val before = record.snapshots.filter { it.atMillis <= record.triggerAtMillis }
        val at = before.lastOrNull() ?: return listOf("$label 시점의 기록이 없습니다.")

        return listOfNotNull(
            headline(record),
            eventsLine(record),
            turnLine(before, record.triggerAtMillis, label),
            brakeLine(before, record.triggerAtMillis, label),
            speedLine(before, at, label),
            steeringLine(at, label),
            conditionLine(at),
        )
    }

    /** 실제로 담긴 구간으로 적는다(계기마다, 이어 붙임마다 길이가 다르다). */
    private fun headline(record: IncidentRecord): String {
        val before = duration(record.triggerAtMillis - (record.snapshots.firstOrNull()?.atMillis ?: record.triggerAtMillis))
        val afterMillis = (record.snapshots.lastOrNull()?.atMillis ?: record.triggerAtMillis) - record.triggerAtMillis
        val span = if (afterMillis < 1_000) "직전 ${before}의" else "직전 ${withAnd(before)} 직후 ${duration(afterMillis)}의"
        return when (record.trigger) {
            Trigger.HARD_STOP -> "급정지가 감지되어 $span 차량 기록을 저장했습니다."
            Trigger.VOICE -> "운전자 요청으로 $span 차량 기록을 저장했습니다."
            Trigger.AIRBAG -> "에어백 전개 신호가 들어와 $span 차량 기록을 저장했습니다."
        }
    }

    /** 기준 시점 말고 다른 감지가 있으면 기준 시점 상대 시각으로 적는다: "−1분 32초 급정지(앞서 저장된 기록을 합침)". */
    private fun eventsLine(record: IncidentRecord): String? {
        val others = record.events.filter { !(it.trigger == record.trigger && it.atMillis == record.triggerAtMillis) }
        if (others.isEmpty()) return null
        val parts = others.map { e ->
            val diff = e.atMillis - record.triggerAtMillis
            val sign = if (diff < 0) "−" else "+"
            "$sign${duration(abs(diff))} ${e.trigger.label}"
        }
        val merged = if (record.mergedRecordIds.isNotEmpty()) " 앞서 저장된 기록을 이 기록에 합쳤습니다." else ""
        return "같은 기록에 다른 감지가 있습니다: ${parts.joinToString(", ")}.$merged"
    }

    private fun turnLine(before: List<Snapshot>, triggerAt: Long, label: String): String {
        val known = before.filter { it.turn != null }
        if (known.isEmpty()) return "방향지시등: 정보 없음."

        // 기준 시점 바로 직전(3초 안)에 켜져 있던 방향지시등을 찾고, 그 방향이 계속 켜져 있던 시작 시점을 거슬러 올라간다.
        val lastOn = known.lastOrNull { it.turn != TurnSignal.OFF && triggerAt - it.atMillis <= 3_000 }
            ?: return "$label 직전에 방향지시등은 켜지지 않았습니다."
        val side = lastOn.turn!!
        var startAt = lastOn.atMillis
        for (s in known.asReversed()) {
            if (s.atMillis > lastOn.atMillis) continue
            if (s.turn == side) startAt = s.atMillis else break
        }
        val sideName = if (side == TurnSignal.LEFT) "좌측" else "우측"
        val since = if (startAt == known.first().atMillis) "기록 시작(${sec(triggerAt - startAt)}초 전) 이전부터" else "${sec(triggerAt - startAt)}초 전부터"
        return "$label $since ${sideName} 방향지시등이 켜져 있었습니다."
    }

    private fun brakeLine(before: List<Snapshot>, triggerAt: Long, label: String): String {
        val known = before.filter { it.brake != null }
        if (known.isEmpty()) return "브레이크: 정보 없음."
        val atBrake = known.last()
        if (atBrake.brake != true) return "$label 시점에 브레이크는 밟지 않은 상태였습니다."

        var start = atBrake
        for (s in known.asReversed()) {
            if (s.brake == true) start = s else break
        }
        val speedAtStart = start.speedKph?.let { " (그때 속도 ${kph(it)}km/h)" } ?: ""
        return "$label ${sec(triggerAt - start.atMillis)}초 전부터 브레이크를 밟고 있었습니다$speedAtStart."
    }

    private fun speedLine(before: List<Snapshot>, at: Snapshot, label: String): String {
        val speeds = before.mapNotNull { it.speedKph }
        if (speeds.isEmpty() || at.speedKph == null) return "속도: 정보 없음."
        return "$label 전까지 최고 속도는 ${kph(speeds.max())}km/h, $label 시점 속도는 ${kph(at.speedKph)}km/h였습니다."
    }

    private fun steeringLine(at: Snapshot, label: String): String {
        val angle = at.steeringDeg ?: return "핸들: 정보 없음."
        if (abs(angle) < straightThresholdDeg) return "$label 시점에 핸들은 거의 정면(${deg(angle)}°)이었습니다."
        val left = if (steeringLeftIsNegative) angle < 0 else angle > 0
        return "$label 시점에 핸들은 ${if (left) "왼쪽" else "오른쪽"}으로 ${deg(abs(angle))}° 돌아가 있었습니다."
    }

    private fun conditionLine(at: Snapshot): String? {
        val parts = buildList {
            if (at.lowBeam == true || at.highBeam == true) add("전조등 켜짐")
            if (at.highBeam == true) add("상향등 켜짐")
            if (at.wiperOn == true) add("와이퍼 작동 중")
        }
        return if (parts.isEmpty()) null else "주변 상태: ${parts.joinToString(", ")}."
    }

    /** "30초", "3분", "1분 32초". */
    private fun duration(millis: Long): String {
        val total = Math.round(millis / 1000.0).toInt()
        val m = total / 60
        val s = total % 60
        return when {
            m == 0 -> "${s}초"
            s == 0 -> "${m}분"
            else -> "${m}분 ${s}초"
        }
    }

    /** "30초와", "3분과": 받침 유무로 와/과. */
    private fun withAnd(word: String): String {
        val last = word.last()
        val hasBatchim = last in '가'..'힣' && (last - '가') % 28 != 0
        return word + if (hasBatchim) "과" else "와"
    }

    private fun sec(millis: Long) = String.format(Locale.ROOT, "%.1f", millis / 1000.0)
    private fun kph(v: Float) = Math.round(v).toString()
    private fun deg(v: Float) = Math.round(v).toString()
}
