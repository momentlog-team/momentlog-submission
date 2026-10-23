package team.momentlog.core

/**
 * 급정지 감지. 감속 "폭"이 아니라 감속 "세기"로 본다.
 *
 * 최근 [windowMillis] 안의 어떤 시점과 지금을 비교해, 속도가 [minDropKph] 이상 줄었고
 * 초당 감속이 [decelKphPerSec] 이상이면 급정지로 본다.
 * - 저속 추돌(14→0km/h를 0.1초)은 폭이 작아도 세기가 커서 잡힌다.
 * - 천천히 서는 정상 감속(40→0km/h를 10초)은 잡히지 않는다.
 *
 * 25km/h/s는 약 0.7g. 1차 기준값이며 실측으로 조정한다.
 * 한 번 감지하면 [cooldownMillis] 동안은 다시 감지하지 않는다(같은 사고를 여러 건으로 나누지 않게).
 */
class HardStopDetector(
    private val decelKphPerSec: Float = 25f,
    private val minDropKph: Float = 8f,
    private val windowMillis: Long = 1_500,
    private val cooldownMillis: Long = 10_000,
) {
    private val recent = ArrayDeque<Pair<Long, Float>>()
    private var lastTriggerAt: Long? = null

    /** 급정지로 판단되면 true. 속도가 없는 스냅샷은 무시한다. */
    fun feed(snapshot: Snapshot): Boolean {
        val speed = snapshot.speedKph ?: return false
        val now = snapshot.atMillis
        recent.addLast(now to speed)
        while (recent.isNotEmpty() && recent.first().first < now - windowMillis) recent.removeFirst()

        val inCooldown = lastTriggerAt?.let { now - it < cooldownMillis } ?: false
        if (inCooldown) return false

        val hard = recent.any { (t, v) ->
            val drop = v - speed
            val seconds = (now - t) / 1000f
            seconds > 0f && drop >= minDropKph && drop / seconds >= decelKphPerSec
        }
        if (!hard) return false

        lastTriggerAt = now
        return true
    }
}
