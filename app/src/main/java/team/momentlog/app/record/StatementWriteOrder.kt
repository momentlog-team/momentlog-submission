package team.momentlog.app.record

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 화면 이탈의 늦은 초안이 같은 내용의 저장 완료 상태를 덮어쓰지 않도록 한다. */
internal class StatementWriteOrder {
    private data class Written(val revision: Long, val confirmed: Boolean)
    private val written = mutableMapOf<String, Written>()
    private val mutex = Mutex()

    suspend fun write(id: String, revision: Long, confirmed: Boolean, save: suspend () -> Unit) {
        mutex.withLock {
            val previous = written[id]
            if (previous != null && (previous.revision > revision ||
                    previous.revision == revision && previous.confirmed && !confirmed)) return
            save()
            written[id] = Written(revision, confirmed)
        }
    }
}
