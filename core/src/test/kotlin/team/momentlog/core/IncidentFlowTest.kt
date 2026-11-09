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

    /** 일정 속도로 달리는 스냅샷을 100ms 간격으로 넣고, 완성된 기록이 있으면 모아 돌려준다. */
    private fun IncidentRecorder.drive(range: LongRange, speed: (Long) -> Float = { 50f }): List<IncidentRecord> =
        range.step(100).mapNotNull { t -> onSnapshot(Snapshot(t, speedKph = speed(t))) }

    @Test
    fun `기록해는 직전 3분과 말한 뒤 30초를 남긴다`() {
        val recorder = IncidentRecorder(newId = { "voice-1" })
        recorder.drive(0L..300_000L)
        recorder.trigger(Trigger.VOICE, 300_000)
        assertTrue(recorder.drive(300_100L..329_900L).isEmpty(), "30초가 지나기 전에는 완성되지 않는다")
        val r = recorder.drive(330_000L..330_000L).single()

        assertEquals(Trigger.VOICE, r.trigger)
        assertEquals(120_000, r.snapshots.first().atMillis)
        assertEquals(330_000, r.snapshots.last().atMillis)
    }

    @Test
    fun `정차 중 사후 저장은 직전 3분만 바로 남긴다`() {
        val recorder = IncidentRecorder(newId = { "after-1" })
        recorder.drive(0L..200_000L) { 0f }
        recorder.trigger(Trigger.VOICE, 200_000, postMillis = 0)
        val r = recorder.drive(200_100L..200_100L).single()
        assertEquals(20_000, r.snapshots.first().atMillis)
        assertTrue(r.snapshots.last().atMillis <= 200_100)
    }

    @Test
    fun `저장 중 다시 급정지하면 같은 기록에 이어 붙이고 멈춘 뒤 10초까지 남긴다`() {
        val recorder = IncidentRecorder(newId = { "follow-1" })
        recorder.drive(0L..40_000L)
        recorder.trigger(Trigger.AIRBAG, 40_000)
        // 충격 뒤에도 계속 달리다가 +12초에 50 → 20km/h 급정지, 3초 동안 천천히 서서 55초에 정지
        val records = recorder.drive(40_100L..90_000L) { t ->
            when {
                t < 52_000 -> 50f
                t < 55_000 -> 20f - (t - 52_000) / 3_000f * 20f
                else -> 0f
            }
        }

        val r = records.single()
        assertEquals(listOf(Trigger.AIRBAG, Trigger.HARD_STOP), r.events.map { it.trigger })
        assertEquals(52_000, r.events[1].atMillis)
        assertEquals(64_900, r.snapshots.last().atMillis, "1km/h 미만이 된 54.9초를 멈춘 시점으로 보고 10초 뒤까지 남긴다")
    }

    @Test
    fun `멈추지 않으면 감지 뒤 60초에서 끝낸다`() {
        val recorder = IncidentRecorder(newId = { "nostop-1" })
        recorder.drive(0L..40_000L)
        recorder.trigger(Trigger.AIRBAG, 40_000)
        val r = recorder.drive(40_100L..120_000L).single()
        assertEquals(100_000, r.snapshots.last().atMillis)
    }

    @Test
    fun `기록해 직전 3분 안의 급정지 기록은 합친다`() {
        var n = 0
        val recorder = IncidentRecorder(newId = { "r-${++n}" })
        val first = recorder.drive(0L..70_000L) { t -> if (t >= 40_000) 0f else 50f }.single()
        assertEquals(Trigger.HARD_STOP, first.trigger)

        recorder.drive(70_100L..132_000L) { 0f }
        recorder.trigger(Trigger.VOICE, 132_000)
        val merged = recorder.drive(132_100L..162_000L) { 0f }.single()

        assertEquals(listOf("r-1"), merged.mergedRecordIds)
        assertEquals(listOf(Trigger.HARD_STOP, Trigger.VOICE), merged.events.map { it.trigger })
        assertTrue(merged.snapshots.first().atMillis <= 40_000, "합친 급정지 시점이 기록 안에 있다")
        val lines = IncidentNarrator().narrate(merged)
        assertTrue(lines.any { it.contains("−1분 32초 급정지") && it.contains("합쳤습니다") }, lines.toString())
    }

    @Test
    fun `경위 첫 문장은 실제로 담긴 구간을 적는다`() {
        val snaps = (120_000L..330_000L step 1_000).map { Snapshot(it, speedKph = 50f) }
        val lines = IncidentNarrator().narrate(IncidentRecord("v-2", Trigger.VOICE, 300_000, snaps))
        assertEquals("운전자 요청으로 직전 3분과 직후 30초의 차량 기록을 저장했습니다.", lines.first())
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

        val followed = record.copy(events = record.events + IncidentEvent(Trigger.HARD_STOP, 52_000))
        assertNotEquals(RecordHasher.sha256(record), RecordHasher.sha256(followed), "이어 붙인 감지도 원본에 포함된다")
    }

    @Test
    fun `원본 문자열을 되돌린 기록은 같은 원본과 해시를 만든다`() {
        val record = IncidentRecord(
            "p-1", Trigger.VOICE, 40_000,
            intersectionScenario().map { it.copy(latitude = 37.5045, longitude = 127.0488) },
            events = listOf(IncidentEvent(Trigger.HARD_STOP, 31_000), IncidentEvent(Trigger.VOICE, 40_000)),
            mergedRecordIds = listOf("old-1"),
        )
        val canonical = RecordHasher.canonical(record)
        val parsed = RecordHasher.parse(canonical)

        assertEquals(canonical, RecordHasher.canonical(parsed))
        assertEquals(RecordHasher.sha256(record), RecordHasher.sha256(parsed))
        assertEquals(IncidentNarrator().narrate(record), IncidentNarrator().narrate(parsed))
    }

    @Test
    fun `음수 0은 0과 같은 원본이 된다(저장 후 다시 읽어도 해시가 같다)`() {
        val base = IncidentRecord("z-1", Trigger.HARD_STOP, 40_000, intersectionScenario())
        val negZero = base.copy(snapshots = base.snapshots.map { it.copy(steeringDeg = -0.0f, latitude = -0.0) })
        val zero = base.copy(snapshots = base.snapshots.map { it.copy(steeringDeg = 0.0f, latitude = 0.0) })
        assertEquals(RecordHasher.sha256(zero), RecordHasher.sha256(negZero))
        assertTrue(!RecordHasher.canonical(negZero).contains("-0.0"))
    }
}
