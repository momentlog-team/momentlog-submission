package team.momentlog.app.ui.models

import team.momentlog.core.Snapshot

/** 그래프와 차량이 같은 시각의 신호를 쓰도록 마지막으로 수신된 스냅샷을 찾는다. */
fun RecordInfo.snapshotAt(atMillis: Long): Snapshot? {
    if (snapshots.isEmpty()) return null
    val index = snapshots.binarySearchBy(atMillis) { it.atMillis }
    return snapshots[if (index >= 0) index else (-index - 2).coerceAtLeast(0)]
}
