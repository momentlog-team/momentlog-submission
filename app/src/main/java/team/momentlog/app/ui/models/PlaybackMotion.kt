package team.momentlog.app.ui.models

import kotlin.math.tan
import kotlin.math.tanh

data class PlaybackMotion(val distanceMeters: Float, val headingDegrees: Float)

/** 신호를 주행 동작으로 시각화한다. 조향비·축거는 그림용 값이며 실측 주행 궤적이 아니다. */
fun RecordInfo.motionAt(playhead: Long, start: Long): PlaybackMotion {
    var distance = 0.0
    var heading = 0.0
    for (index in snapshots.indices) {
        val s = snapshots[index]
        val from = maxOf(start, s.atMillis)
        val to = minOf(playhead, snapshots.getOrNull(index + 1)?.atMillis ?: playhead)
        if (to <= from) continue
        val meters = (s.speedKph ?: 0f).coerceAtLeast(0f) / 3.6 * (to - from) / 1000.0
        distance += meters
        val wheel = Math.toRadians((s.steeringDeg ?: 0f).coerceIn(-540f, 540f) / 15.0)
        heading += Math.toDegrees(meters / 2.8 * tan(wheel))
    }
    // 최대 회전각에 닿을 때도 갑자기 멈추지 않도록 완만하게 수렴시킨다.
    return PlaybackMotion(distance.toFloat(), (22.0 * tanh(heading / 22.0)).toFloat())
}
