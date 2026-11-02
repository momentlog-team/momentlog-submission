package team.momentlog.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImpactFlowTest {

    /**
     * 시연 A(교차로 후방 추돌): 32km/h로 오다가 6.2초 전 우측 지시등, 4.1초 전부터 브레이크로 12km/h까지 서서히 감속.
     * 0초에 뒤에서 받혀 브레이크를 밟은 채 12 → 15km/h로 밀리고, 3초 동안 서서히 멈춘다. 급정지 조건은 충족하지 않는다.
     */
    private fun rearEndScenario(collisionAt: Long = 40_000): List<Snapshot> =
        (collisionAt - 35_000..collisionAt + 15_000 step 100).map { t ->
            val toCollision = collisionAt - t
            val speed = when {
                toCollision > 4_100 -> 32f
                toCollision > 0 -> 12f + toCollision / 4_100f * 20f
                toCollision == 0L -> 12f
                t - collisionAt <= 100 -> 15f
                else -> maxOf(0f, 15f - (t - collisionAt - 100) / 2_900f * 15f)
            }
            Snapshot(
                atMillis = t,
                speedKph = speed,
                brake = toCollision <= 4_100,
                turn = if (toCollision <= 6_200) TurnSignal.RIGHT else TurnSignal.OFF,
                steeringDeg = 0f,
                lowBeam = true,
                wiperOn = false,
            )
        }

    @Test
    fun `브레이크를 밟은 채 갑자기 빨라지면 뒤에서 밀린 충격으로 본다`() {
        val detector = ImpactDetector()
        assertFalse(detector.feed(Snapshot(0, speedKph = 12f, brake = true)))
        assertTrue(detector.feed(Snapshot(100, speedKph = 15f, brake = true)))
    }

    @Test
    fun `신호 대기 중 정차 상태에서 밀려도 충격으로 본다`() {
        val detector = ImpactDetector()
        assertFalse(detector.feed(Snapshot(0, speedKph = 0f, brake = true)))
        assertTrue(detector.feed(Snapshot(100, speedKph = 6f, brake = true)))
    }

    @Test
    fun `가속 페달로 빨라지거나 브레이크 밟고 천천히 밀리는 건 충격이 아니다`() {
        val accelerating = ImpactDetector()
        assertFalse(accelerating.feed(Snapshot(0, speedKph = 12f, brake = false)))
        assertFalse(accelerating.feed(Snapshot(100, speedKph = 20f, brake = false)))

        val creeping = ImpactDetector()
        val fired = (0L..3_000L step 100).any { t -> creeping.feed(Snapshot(t, speedKph = t / 3_000f * 2.5f, brake = true)) }
        assertFalse(fired, "내리막에서 3초에 걸쳐 2.5km/h 밀리는 건 잡지 않는다")

        val braking = ImpactDetector()
        assertFalse((0L..2_000L step 100).any { t -> braking.feed(Snapshot(t, speedKph = 40f - t / 50f, brake = true)) })
    }

    @Test
    fun `수평 가속도가 크면 충격, 급제동 수준이면 아니다`() {
        val detector = ImpactDetector()
        assertFalse(detector.feedAcceleration(0, -9.8f, 0f), "1g 급제동")
        assertTrue(detector.feedAcceleration(100, -12f, 10f), "수평 합 약 1.6g")
        assertFalse(detector.feedAcceleration(5_000, 20f, 0f), "10초 안에는 다시 잡지 않는다")
        assertTrue(detector.feedAcceleration(10_100, 20f, 0f))
    }

    @Test
    fun `서서히 서다가 받히면 급정지가 아니라 충격 기록이 남는다`() {
        val recorder = IncidentRecorder(newId = { "a-1" })
        var record: IncidentRecord? = null
        rearEndScenario().forEach { s -> recorder.onSnapshot(s)?.let { record = it } }

        val r = assertNotNull(record)
        assertEquals(Trigger.IMPACT, r.trigger)
        assertEquals(listOf(Trigger.IMPACT), r.events.map { it.trigger }, "급정지는 따로 잡히지 않는다")
        assertEquals(40_100, r.triggerAtMillis, "밀린 첫 스냅샷")
        assertTrue(r.snapshots.first().atMillis <= r.triggerAtMillis - 29_000)
        assertEquals(52_900, r.snapshots.last().atMillis, "1km/h 미만이 된 42.9초를 멈춘 시점으로 보고 10초 뒤까지 남긴다")
    }

    @Test
    fun `가속도 충격은 정차 중에도 기록을 시작한다`() {
        val recorder = IncidentRecorder(newId = { "c-1" })
        (0L..40_000L step 100).forEach { recorder.onSnapshot(Snapshot(it, speedKph = 0f, brake = true)) }
        recorder.onAcceleration(40_050, 0f, 16f)
        assertTrue(recorder.isCapturing)
        assertEquals(Trigger.IMPACT, recorder.pendingTrigger)

        val r = (40_100L..51_000L step 100).firstNotNullOf { recorder.onSnapshot(Snapshot(it, speedKph = 0f, brake = true)) }
        assertEquals(Trigger.IMPACT, r.trigger)
        assertEquals(40_050, r.triggerAtMillis)
        assertEquals(50_100, r.snapshots.last().atMillis)
        assertNull(recorder.pendingTrigger)
    }

    @Test
    fun `급정지 저장 중 충격이 오면 같은 기록에 이어 붙인다`() {
        val recorder = IncidentRecorder(newId = { "b-1" })
        (0L..40_000L step 100).forEach { recorder.onSnapshot(Snapshot(it, speedKph = 50f)) }
        recorder.onSnapshot(Snapshot(40_100, speedKph = 20f))
        assertEquals(Trigger.HARD_STOP, recorder.pendingTrigger)
        recorder.onAcceleration(40_300, 18f, 0f)
        assertEquals(listOf(Trigger.HARD_STOP, Trigger.IMPACT), recorder.pendingEvents.map { it.trigger })
    }

    @Test
    fun `경위 문장은 충격이면 충돌, 급정지면 급정지, 기록해면 기록 요청 기준으로 적는다`() {
        val snaps = rearEndScenario()
        val impact = IncidentNarrator().narrate(IncidentRecord("n-1", Trigger.IMPACT, 40_000, snaps))
        println(impact.joinToString("\n"))
        assertTrue(impact.first().startsWith("충격이 감지되어"), impact.toString())
        assertTrue(impact.any { it.contains("충돌 6.2초 전부터 우측 방향지시등") }, impact.toString())
        assertTrue(impact.any { it.contains("충돌 4.1초 전부터 브레이크") }, impact.toString())
        assertTrue(impact.any { it.contains("충돌 시점 속도는 12km/h") }, impact.toString())

        val hardStop = IncidentNarrator().narrate(IncidentRecord("n-2", Trigger.HARD_STOP, 40_000, snaps))
        assertTrue(hardStop.any { it.contains("급정지 4.1초 전부터 브레이크") }, hardStop.toString())
        assertFalse(hardStop.any { it.contains("충돌") }, "급정지만으로는 충돌이라고 쓰지 않는다")

        val voice = IncidentNarrator().narrate(IncidentRecord("n-3", Trigger.VOICE, 40_000, snaps))
        assertTrue(voice.any { it.contains("기록 요청 4.1초 전부터 브레이크") }, voice.toString())

        val airbag = IncidentNarrator().narrate(IncidentRecord("n-4", Trigger.AIRBAG, 40_000, snaps))
        assertTrue(airbag.any { it.contains("충돌 4.1초 전부터 브레이크") }, airbag.toString())
    }

    @Test
    fun `충격 기록도 원본 문자열로 되돌릴 수 있다`() {
        val record = IncidentRecord(
            "p-2", Trigger.IMPACT, 40_100, rearEndScenario(),
            events = listOf(IncidentEvent(Trigger.IMPACT, 40_100), IncidentEvent(Trigger.HARD_STOP, 52_000)),
        )
        val parsed = RecordHasher.parse(RecordHasher.canonical(record))
        assertEquals(RecordHasher.sha256(record), RecordHasher.sha256(parsed))
        assertEquals(Trigger.IMPACT, parsed.trigger)
        assertTrue(Trigger.IMPACT.isCollision && Trigger.AIRBAG.isCollision)
        assertFalse(Trigger.HARD_STOP.isCollision || Trigger.VOICE.isCollision)
    }
}
