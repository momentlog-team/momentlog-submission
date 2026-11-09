package team.momentlog.core

import java.security.MessageDigest
import java.util.Locale

/**
 * 기록 원본의 해시. 저장 직후 이 값을 서버에 등록해 서버 시각으로 고정하고,
 * 나중에 제출한 문서가 원본과 같은지 대조할 때 쓴다.
 *
 * 같은 기록이면 어느 기기에서 계산해도 같은 값이 나오도록 직렬화 순서와 숫자 형식을 고정한다.
 * 이 형식을 바꾸면 기존 기록과 대조가 깨지므로 [FORMAT_VERSION]을 올린다.
 */
object RecordHasher {
    const val FORMAT_VERSION = 2

    fun canonical(record: IncidentRecord): String = buildString {
        append("v=").append(FORMAT_VERSION).append('\n')
        append("id=").append(record.id).append('\n')
        append("trigger=").append(record.trigger.name).append('\n')
        append("triggerAt=").append(record.triggerAtMillis).append('\n')
        // v2: 이어 붙인 감지와 합친 앞선 기록
        record.events.forEach { e -> append("event=").append(e.trigger.name).append('@').append(e.atMillis).append('\n') }
        record.mergedRecordIds.forEach { append("merged=").append(it).append('\n') }
        record.snapshots.forEach { s ->
            append(s.atMillis).append('|')
                .append(num(s.speedKph)).append('|')
                .append(s.brake?.toString() ?: "").append('|')
                .append(s.turn?.name ?: "").append('|')
                .append(num(s.steeringDeg)).append('|')
                .append(s.lowBeam?.toString() ?: "").append('|')
                .append(s.highBeam?.toString() ?: "").append('|')
                .append(s.wiperOn?.toString() ?: "").append('|')
                .append(coord(s.latitude)).append('|')
                .append(coord(s.longitude)).append('\n')
        }
    }

    fun sha256(record: IncidentRecord): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonical(record).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * [canonical] 문자열을 기록으로 되돌린다. 서버가 봉인된 원본으로 문서를 만들 때 쓴다.
     * 숫자는 직렬화할 때 반올림한 값이므로, 되돌린 기록을 다시 [canonical]로 만들면 원래 문자열과 같다.
     */
    fun parse(canonical: String): IncidentRecord {
        var version: Int? = null
        var id: String? = null
        var trigger: Trigger? = null
        var triggerAt: Long? = null
        val events = mutableListOf<IncidentEvent>()
        val merged = mutableListOf<String>()
        val snapshots = mutableListOf<Snapshot>()
        canonical.split('\n').filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("v=") -> version = line.substring(2).toInt()
                line.startsWith("id=") -> id = line.substring(3)
                line.startsWith("trigger=") -> trigger = Trigger.valueOf(line.substring(8))
                line.startsWith("triggerAt=") -> triggerAt = line.substring(10).toLong()
                line.startsWith("event=") -> {
                    val (t, at) = line.substring(6).split('@')
                    events += IncidentEvent(Trigger.valueOf(t), at.toLong())
                }
                line.startsWith("merged=") -> merged += line.substring(7)
                else -> snapshots += snapshotOf(line)
            }
        }
        require(version == FORMAT_VERSION) { "지원하지 않는 형식 버전: $version" }
        return IncidentRecord(
            id = requireNotNull(id) { "id 없음" },
            trigger = requireNotNull(trigger) { "trigger 없음" },
            triggerAtMillis = requireNotNull(triggerAt) { "triggerAt 없음" },
            snapshots = snapshots,
            events = events,
            mergedRecordIds = merged,
        )
    }

    private fun snapshotOf(line: String): Snapshot {
        val f = line.split('|')
        require(f.size == 10) { "스냅샷 열 개수가 10이 아님: $line" }
        return Snapshot(
            atMillis = f[0].toLong(),
            speedKph = f[1].ifEmpty { null }?.toFloat(),
            brake = f[2].ifEmpty { null }?.toBooleanStrict(),
            turn = f[3].ifEmpty { null }?.let { TurnSignal.valueOf(it) },
            steeringDeg = f[4].ifEmpty { null }?.toFloat(),
            lowBeam = f[5].ifEmpty { null }?.toBooleanStrict(),
            highBeam = f[6].ifEmpty { null }?.toBooleanStrict(),
            wiperOn = f[7].ifEmpty { null }?.toBooleanStrict(),
            latitude = f[8].ifEmpty { null }?.toDouble(),
            longitude = f[9].ifEmpty { null }?.toDouble(),
        )
    }

    private fun num(v: Float?) = v?.let { unsignedZero(String.format(Locale.ROOT, "%.2f", it)) } ?: ""
    private fun coord(v: Double?) = v?.let { unsignedZero(String.format(Locale.ROOT, "%.6f", it)) } ?: ""

    /**
     * "-0.00"을 "0.00"으로. -0.0은 JSON에 저장했다 읽으면 0이 되므로, 그대로 두면 저장 전과 후의 해시가 달라진다
     * (조향각 부호를 뒤집을 때 생겼던 문제, 11/9 시연 중 발견).
     */
    private fun unsignedZero(s: String) = if (s.startsWith("-") && s.all { it == '-' || it == '0' || it == '.' }) s.substring(1) else s
}
