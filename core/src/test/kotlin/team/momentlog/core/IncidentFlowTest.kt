package team.momentlog.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncidentFlowTest {

    /** 교차로 우회전 시나리오: 32km/h로 오다가 6.2초 전 우측 지시등, 4.1초 전 브레이크, 0초에 급정지. 100ms 간격. */
    private fun intersectionScenario(triggerAt: Long = 40_000): List<Snapshot> {
        val start = triggerAt - 35_000
        return (start..triggerAt + 12_000 step 100).map { t ->
            val toTrigger = triggerAt - t
            val speed = when {
                toTrigger > 4_100 -> 32f
                toTrigger > 1_000 -> 32f - (4_100 - toTrigger) / 3_100f * 18f // 32 → 14 서서히 감속
                toTrigger > 0 -> 14f
                else -> 0f // 충돌로 급정지
            }
            Snapshot(
                atMillis = t,
                speedKph = speed,
                brake = toTrigger <= 4_100,
                turn = if (toTrigger <= 6_200) TurnSignal.RIGHT else TurnSignal.OFF,
                steeringDeg = if (toTrigger <= 1_500) 12f else 0f,
                lowBeam = false,
                wiperOn = false,
            )
        }
    }

    @Test
    fun `버퍼는 최근 30초만 남긴다`() {
        val buffer = SnapshotBuffer(30_000)
        (0L..60_000L step 1_000).forEach { buffer.add(Snapshot(atMillis = it)) }
        val kept = buffer.toList()
        assertEquals(30_000, kept.first().atMillis)
        assertEquals(60_000, kept.last().atMillis)
    }

    @Test
    fun `짧은 시간에 세게 줄면 급정지, 천천히 줄면 아님`() {
        val sudden = HardStopDetector()
        assertFalse(sudden.feed(Snapshot(0, speedKph = 40f)))
        assertTrue(sudden.feed(Snapshot(1_000, speedKph = 15f)))

        val lowSpeedCrash = HardStopDetector()
        assertFalse(lowSpeedCrash.feed(Snapshot(0, speedKph = 14f)))
        assertTrue(lowSpeedCrash.feed(Snapshot(100, speedKph = 0f)))

        val gentle = HardStopDetector()
        val fired = (0L..10_000L step 100).any { t -> gentle.feed(Snapshot(t, speedKph = 40f - t / 10_000f * 40f)) }
        assertFalse(fired)
    }

    @Test
    fun `작은 흔들림은 급정지로 보지 않는다`() {
        val detector = HardStopDetector()
        assertFalse(detector.feed(Snapshot(0, speedKph = 30f)))
        assertFalse(detector.feed(Snapshot(100, speedKph = 26f))) // 4km/h 출렁임: 세기는 크지만 폭이 작다
    }

    @Test
    fun `급정지 후 10초가 지나면 앞30초와 뒤10초가 담긴 기록이 나온다`() {
        val recorder = IncidentRecorder(newId = { "test-1" })
        var record: IncidentRecord? = null
        intersectionScenario().forEach { s -> recorder.onSnapshot(s)?.let { record = it } }

        val r = assertNotNull(record)
        assertEquals(Trigger.HARD_STOP, r.trigger)
        assertTrue(r.snapshots.first().atMillis <= r.triggerAtMillis - 29_000)
        assertTrue(r.snapshots.last().atMillis >= r.triggerAtMillis + 10_000)
        assertFalse(recorder.isCapturing)
    }

    @Test
    fun `음성 요청으로도 기록을 만들 수 있다`() {
        val recorder = IncidentRecorder(newId = { "voice-1" })
        (0L..20_000L step 100).forEach { recorder.onSnapshot(Snapshot(it, speedKph = 50f)) }
        recorder.trigger(Trigger.VOICE, 20_000)
        var record: IncidentRecord? = null
        (20_100L..31_000L step 100).forEach { t -> recorder.onSnapshot(Snapshot(t, speedKph = 50f))?.let { record = it } }
        assertEquals(Trigger.VOICE, assertNotNull(record).trigger)
    }

    @Test
    fun `경위 문장에 지시등 시작, 브레이크 시작, 속도, 핸들이 들어간다`() {
        val record = IncidentRecord("n-1", Trigger.HARD_STOP, 40_000, intersectionScenario())
        val lines = IncidentNarrator().narrate(record)
        println(lines.joinToString("\n"))

        assertTrue(lines.any { it.contains("6.2초 전부터 우측 방향지시등") }, lines.toString())
        assertTrue(lines.any { it.contains("4.1초 전부터 브레이크") }, lines.toString())
        assertTrue(lines.any { it.contains("최고 속도는 32km/h") }, lines.toString())
        assertTrue(lines.any { it.contains("오른쪽으로 12°") }, lines.toString())
    }

    @Test
    fun `조향각이 음수면 왼쪽으로 적는다`() {
        val snaps = intersectionScenario().map { it.copy(steeringDeg = -120f) }
        val lines = IncidentNarrator().narrate(IncidentRecord("n-4", Trigger.HARD_STOP, 40_000, snaps))
        assertTrue(lines.any { it.contains("왼쪽으로 120°") }, lines.toString())
    }

    @Test
    fun `지시등을 안 켰으면 그렇게 적는다`() {
        val snaps = intersectionScenario().map { it.copy(turn = TurnSignal.OFF) }
        val lines = IncidentNarrator().narrate(IncidentRecord("n-2", Trigger.HARD_STOP, 40_000, snaps))
        assertTrue(lines.any { it.contains("방향지시등은 켜지지 않았습니다") }, lines.toString())
    }

    @Test
    fun `값이 없는 항목은 정보 없음으로 남긴다`() {
        val snaps = intersectionScenario().map { Snapshot(it.atMillis, speedKph = it.speedKph) }
        val lines = IncidentNarrator().narrate(IncidentRecord("n-3", Trigger.HARD_STOP, 40_000, snaps))
        assertTrue(lines.contains("방향지시등: 정보 없음."))
        assertTrue(lines.contains("브레이크: 정보 없음."))
        assertNull(lines.firstOrNull { it.startsWith("주변 상태") })
    }

    @Test
    fun `해시는 같은 기록이면 같고 숫자 하나만 바꿔도 달라진다`() {
        val record = IncidentRecord("h-1", Trigger.HARD_STOP, 40_000, intersectionScenario())
        val same = record.copy(snapshots = record.snapshots.toList())
        assertEquals(RecordHasher.sha256(record), RecordHasher.sha256(same))

        val tampered = record.copy(snapshots = record.snapshots.map {
            if (it.atMillis == 34_000L) it.copy(turn = TurnSignal.OFF) else it
        })
        assertNotEquals(RecordHasher.sha256(record), RecordHasher.sha256(tampered))
        assertEquals(64, RecordHasher.sha256(record).length)
    }
}
