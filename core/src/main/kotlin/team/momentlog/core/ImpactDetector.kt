package team.momentlog.core

import kotlin.math.sqrt

/**
 * 충격 감지. 급정지로는 안 잡히는 사고(신호 대기 중 추돌, 서서히 서다가 받힘, 주차장 저속 접촉)를 잡는다.
 * 급정지만 기준으로 하면 저장 자체가 "갑자기 섰다"는 상대 주장을 뒷받침하게 되므로 따로 둔다.
 *
 * 두 가지로 본다. 1차 기준값이며 실측으로 조정한다.
 * - 뒤에서 밀림 [feed]: 브레이크를 밟은 채 [pushWindowMillis] 안에 속도가 [minRiseKph] 이상 오른다.
 *   운전자가 속도를 올릴 수 없는 상태에서 차가 빨라졌으니 밖에서 힘을 받은 것이다.
 * - 수평 충격 [feedAcceleration]: 헤드유닛 가속도 센서의 수평 성분이 [shockMps2](약 1.5g) 이상.
 *   마른 노면 급제동이 1g 안팎이라 그보다 높게 둔다.
 *
 * 한 번 감지하면 [cooldownMillis] 동안은 다시 감지하지 않는다(한 번 받힌 걸 여러 번으로 나누지 않게).
 */
class ImpactDetector(
    private val minRiseKph: Float = 2f,
    private val pushWindowMillis: Long = 500,
    private val shockMps2: Float = 14.7f,
    private val cooldownMillis: Long = 10_000,
) {
    private val braking = ArrayDeque<Pair<Long, Float>>()
    private var lastTriggerAt: Long? = null

    /** 뒤에서 밀렸다고 판단되면 true. 브레이크를 떼면 비교 구간을 비운다. */
    fun feed(snapshot: Snapshot): Boolean {
        val speed = snapshot.speedKph ?: return false
        if (snapshot.brake != true) {
            braking.clear()
            return false
        }
        val now = snapshot.atMillis
        braking.addLast(now to speed)
        while (braking.isNotEmpty() && braking.first().first < now - pushWindowMillis) braking.removeFirst()

        val pushed = braking.any { (t, v) -> t < now && speed - v >= minRiseKph }
        return pushed && fire(now)
    }

    /** 수평 충격이면 true. [xMps2], [yMps2]는 중력을 뺀 차량 수평 성분. */
    fun feedAcceleration(atMillis: Long, xMps2: Float, yMps2: Float): Boolean {
        val horizontal = sqrt(xMps2 * xMps2 + yMps2 * yMps2)
        return horizontal >= shockMps2 && fire(atMillis)
    }

    private fun fire(now: Long): Boolean {
        val inCooldown = lastTriggerAt?.let { now - it < cooldownMillis } ?: false
        if (inCooldown) return false
        lastTriggerAt = now
        return true
    }
}
