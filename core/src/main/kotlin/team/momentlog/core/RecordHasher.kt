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

    private fun num(v: Float?) = v?.let { String.format(Locale.ROOT, "%.2f", it) } ?: ""
    private fun coord(v: Double?) = v?.let { String.format(Locale.ROOT, "%.6f", it) } ?: ""
}
