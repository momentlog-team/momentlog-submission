package team.momentlog.app.record

import kotlinx.coroutines.sync.Mutex

/** 공유 생성이 진행 중이면 완료 후 철회한다. 철회 뒤 늦은 생성 응답으로 링크가 다시 생기지 않게 한다. */
internal object RecordRemoteAccess {
    val mutex = Mutex()
}
