package team.momentlog.app.ui.models

import team.momentlog.app.record.RecordStore
import team.momentlog.core.IncidentNarrator
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger

/** 화면에 보여 줄 기록 한 건. 저장소 형식([RecordStore.Saved])을 화면용으로 바꾼다. */
data class RecordInfo(
    val id: String,
    val trigger: Trigger,
    val triggeredAtMillis: Long,
    val sha256: String,
    val sealed: Boolean,
    val narrative: List<String>,
    val snapshots: List<Snapshot>,
) {
    val triggerLabel: String get() = trigger.label

    companion object {
        fun from(saved: RecordStore.Saved, narrator: IncidentNarrator) = RecordInfo(
            id = saved.record.id,
            trigger = saved.record.trigger,
            triggeredAtMillis = saved.record.triggerAtMillis,
            sha256 = saved.sha256,
            sealed = saved.sealed,
            narrative = narrator.narrate(saved.record),
            snapshots = saved.record.snapshots,
        )
    }
}
