package team.momentlog.core

/**
 * 최근 [windowMillis] 동안의 스냅샷만 메모리에 들고 있는 링 버퍼.
 * 평소에는 아무것도 디스크에 쓰지 않는다. 사고가 났을 때만 [toList]로 꺼내 저장한다.
 */
class SnapshotBuffer(private val windowMillis: Long = 30_000) {
    private val items = ArrayDeque<Snapshot>()

    @Synchronized
    fun add(snapshot: Snapshot) {
        items.addLast(snapshot)
        val oldest = snapshot.atMillis - windowMillis
        while (items.isNotEmpty() && items.first().atMillis < oldest) items.removeFirst()
    }

    @Synchronized
    fun toList(): List<Snapshot> = items.toList()

    @Synchronized
    fun latest(): Snapshot? = items.lastOrNull()

    @Synchronized
    fun clear() = items.clear()

    val size: Int @Synchronized get() = items.size
}
