package team.momentlog.app.record

import team.momentlog.core.IncidentRecord
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger
import team.momentlog.core.TurnSignal

/** 디버그 빌드의 로컬 시연 데이터. 실제 기록과 같은 저장·조회 경로를 거친다. */
internal object MockRecords {
    fun create(nowMillis: Long): List<IncidentRecord> {
        data class Scenario(val id: String, val turn: TurnSignal, val steer: Float?, val speed: Float, val brake: Boolean?, val trigger: Trigger)
        val scenarios = listOf(
            Scenario("left-brake", TurnSignal.LEFT, -120f, 8f, true, Trigger.HARD_STOP),
            Scenario("right-brake", TurnSignal.RIGHT, 180f, 12f, true, Trigger.HARD_STOP),
            Scenario("straight-stop", TurnSignal.OFF, 0f, 0f, true, Trigger.HARD_STOP),
            Scenario("parking-left", TurnSignal.LEFT, -450f, 5f, true, Trigger.AIRBAG),
            Scenario("right-cruise", TurnSignal.RIGHT, 90f, 41f, false, Trigger.VOICE),
            Scenario("unavailable", TurnSignal.OFF, null, 0f, null, Trigger.VOICE),
        )
        return scenarios.mapIndexed { index, s ->
            val triggerAt = nowMillis - (index + 1) * 60_000L
            val snapshots = (-30_000L..10_000L step 100).map { offset ->
                val braking = offset >= -2_400 && s.brake == true
                val deceleration = ((offset + 2_400) / 2_400f).coerceIn(0f, 1f)
                val steering = ((offset + 5_000) / 4_000f).coerceIn(0f, 1f)
                val initialSpeed = if (s.id == "parking-left") 12f else if (s.id == "unavailable") 0f else 65f
                Snapshot(
                    atMillis = triggerAt + offset,
                    speedKph = if (offset > 0 && s.brake == true) (s.speed * (1 - offset / 3_000f)).coerceAtLeast(0f) else initialSpeed + (s.speed - initialSpeed) * deceleration,
                    brake = s.brake?.let { braking },
                    turn = if (offset >= -6_200) s.turn else TurnSignal.OFF,
                    steeringDeg = s.steer?.times(steering),
                    lowBeam = false, highBeam = false,
                )
            }
            IncidentRecord("mock-${s.id}", s.trigger, triggerAt, snapshots)
        }
    }
}
