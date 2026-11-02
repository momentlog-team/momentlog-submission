package team.momentlog.app.ui.models

import androidx.compose.ui.graphics.Color
import team.momentlog.app.config.theme.MomentlogColors
import team.momentlog.app.record.RecordStore
import team.momentlog.app.ui.screens.widget.icon.AppIcon
import team.momentlog.core.IncidentEvent
import team.momentlog.core.IncidentNarrator
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger
import team.momentlog.core.TurnSignal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 저장 계기. 충격 감지는 지금 에어백 전개 신호로만 판단한다.
 */
enum class RecordKind(
    val label: String,
    /** 경위 문장의 기준 표현. "충돌 N초 전"은 충격이 감지된 기록에만 쓴다. */
    val anchor: String,
    val markerLabel: String,
    val icon: AppIcon,
) {
    IMPACT("충격 감지", "충돌", "충격 감지", AppIcon.IMPACT),
    HARD_STOP("급정지", "급정지", "급정지", AppIcon.STOP),
    VOICE("“기록해”", "기록 요청", "기록해", AppIcon.MIC),
    AFTER_STOP("정차 중 저장", "기록 요청", "기록해", AppIcon.REC);

    fun tone(colors: MomentlogColors): Color = when (this) {
        IMPACT -> colors.danger
        HARD_STOP -> colors.textPrimary
        VOICE, AFTER_STOP -> colors.accent
    }

    companion object {
        fun of(event: IncidentEvent) = of(event.trigger, savedWhileParked = false)

        fun of(trigger: Trigger, savedWhileParked: Boolean) = when (trigger) {
            Trigger.AIRBAG, Trigger.IMPACT -> IMPACT
            Trigger.HARD_STOP -> HARD_STOP
            Trigger.VOICE -> if (savedWhileParked) AFTER_STOP else VOICE
        }
    }
}

/** 화면에 보여 줄 기록 한 건. */
data class RecordInfo(
    val id: String,
    val trigger: Trigger,
    val kind: RecordKind,
    val triggeredAtMillis: Long,
    val sha256: String,
    val sealed: Boolean,
    val narrative: List<String>,
    val snapshots: List<Snapshot>,
    val statement: String?,
    val plate: String?,
    val statementState: RecordStore.StatementState,
    val kept: Boolean,
    val deleteAtMillis: Long,
    /** 기준 시점을 포함한 모든 감지(이어 붙임, 합친 앞선 기록). 시각 순서. */
    val events: List<IncidentEvent>,
    /** 앞서 저장된 기록을 합쳤는지. */
    val merged: Boolean,
) {
    val triggerLabel: String get() = trigger.label
    val facts: RecordFacts by lazy { RecordFacts.of(snapshots, triggeredAtMillis) }

    val startMillis: Long get() = snapshots.firstOrNull()?.atMillis ?: triggeredAtMillis
    val endMillis: Long get() = snapshots.lastOrNull()?.atMillis ?: triggeredAtMillis
    val durationSeconds: Int get() = ((endMillis - startMillis) / 1000).toInt()

    /** 보관되지 않은 기록이 지워지기까지 남은 날(올림). 보관된 기록이면 null. */
    fun daysUntilDelete(nowMillis: Long): Int? =
        if (kept) null else ceil((deleteAtMillis - nowMillis) / DAY_MILLIS).toInt().coerceAtLeast(0)

    /** 기준 시점 말고 다른 감지(이어 붙인 감지, 합친 앞선 기록). */
    val otherEvents: List<IncidentEvent>
        get() = events.filterNot { it.trigger == trigger && it.atMillis == triggeredAtMillis }

    /** 진술을 아직 남기지 않았는지("나중에"를 골랐거나 말하다 출발). */
    val needsStatement: Boolean
        get() = statementState != RecordStore.StatementState.DONE

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0

        fun from(saved: RecordStore.Saved, narrator: IncidentNarrator) = RecordInfo(
            id = saved.record.id,
            trigger = saved.record.trigger,
            kind = RecordKind.of(saved.record.trigger, saved.savedWhileParked),
            triggeredAtMillis = saved.record.triggerAtMillis,
            sha256 = saved.sha256,
            sealed = saved.sealed,
            narrative = narrator.narrate(saved.record),
            snapshots = saved.record.snapshots,
            statement = saved.statement,
            plate = saved.plate,
            statementState = saved.statementState,
            kept = saved.isKept,
            deleteAtMillis = saved.deleteAtMillis,
            events = saved.record.events,
            merged = saved.record.mergedRecordIds.isNotEmpty(),
        )
    }
}

/**
 * 기준 시점 직전 상태에서 뽑은 사실들. [IncidentNarrator]와 같은 기준이다. 값을 받지 못한 항목은 null.
 */
data class RecordFacts(
    val atTrigger: Snapshot?,
    /** 기준 시점까지 켜져 있던 방향지시등과 켜진 지 몇 초 전인지. */
    val turn: TurnSignal?,
    val turnSecondsBefore: Float?,
    /** 기준 시점까지 밟고 있던 브레이크가 몇 초 전에 시작됐는지와 그때 속도. */
    val brakeSecondsBefore: Float?,
    val brakeStartSpeed: Float?,
    val speedAtTrigger: Float? = atTrigger?.speedKph,
    val steeringAtTrigger: Float? = atTrigger?.steeringDeg,
) {
    companion object {
        fun of(snapshots: List<Snapshot>, triggerAt: Long): RecordFacts {
            val before = snapshots.filter { it.atMillis <= triggerAt }
            val at = before.lastOrNull()

            val turns = before.filter { it.turn != null }
            val lastOn = turns.lastOrNull { it.turn != TurnSignal.OFF && triggerAt - it.atMillis <= 3_000 }
            var turnStart = lastOn
            if (lastOn != null) {
                for (s in turns.asReversed()) {
                    if (s.atMillis > lastOn.atMillis) continue
                    if (s.turn == lastOn.turn) turnStart = s else break
                }
            }

            val brakes = before.filter { it.brake != null }
            var brakeStart: Snapshot? = null
            if (brakes.lastOrNull()?.brake == true) {
                for (s in brakes.asReversed()) if (s.brake == true) brakeStart = s else break
            }

            return RecordFacts(
                atTrigger = at,
                turn = lastOn?.turn,
                turnSecondsBefore = turnStart?.let { (triggerAt - it.atMillis) / 1000f },
                brakeSecondsBefore = brakeStart?.let { (triggerAt - it.atMillis) / 1000f },
                brakeStartSpeed = brakeStart?.speedKph,
            )
        }
    }
}

/** "6.2초", "1분 32초". */
fun secondsText(seconds: Float): String {
    if (seconds >= 60f) {
        val total = Math.round(seconds)
        return "${total / 60}분 ${total % 60}초"
    }
    val rounded = Math.round(seconds * 10) / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}초" else String.format(Locale.ROOT, "%.1f초", rounded)
}

/** 그래프·단계에 쓰는 기준 시점 상대 표기: "0초", "−6.2초", "+12초", "−1:32". */
fun relativeText(seconds: Float): String {
    if (abs(seconds) < 0.05f) return "0초"
    val sign = if (seconds < 0) "−" else "+"
    val a = abs(seconds)
    if (a >= 60f) {
        val m = (a / 60).toInt()
        val r = Math.round(a % 60)
        return "$sign$m:${r.toString().padStart(2, '0')}"
    }
    val rounded = Math.round(a * 10) / 10f
    return if (rounded % 1f == 0f) "$sign${rounded.toInt()}초" else String.format(Locale.ROOT, "%s%.1f초", sign, rounded)
}

/** 핸들 각도 문장: "정면(0°)", "왼쪽 120°". 기록은 왼쪽이 음수. */
fun steeringText(deg: Float?): String = when {
    deg == null -> "핸들 정보 없음"
    abs(deg) < 5f -> "정면(${Math.round(deg)}°)"
    deg < 0 -> "왼쪽 ${Math.round(abs(deg))}°"
    else -> "오른쪽 ${Math.round(deg)}°"
}

fun turnSideText(turn: TurnSignal?): String = when (turn) {
    TurnSignal.LEFT -> "좌측"
    TurnSignal.RIGHT -> "우측"
    else -> ""
}
