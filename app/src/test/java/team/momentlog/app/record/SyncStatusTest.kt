package team.momentlog.app.record

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncStatusTest {
    @Test fun `offline pending and rejected requests are not presented as completed`() {
        assertEquals("연결을 기다리고 있어요", SyncStatus(pendingSeals = 2, offline = true, checked = true).title)
        assertTrue(SyncStatus(revocationFailures = listOf(401)).needsAttention)
        assertEquals("서버 전송 확인 필요", SyncStatus(sealFailures = listOf(403), checked = true).title)
        assertEquals("서버 전송 완료", SyncStatus(checked = true).title)
        assertEquals("전송 상태 확인 중", SyncStatus().title)
    }

    @Test fun `authentication ownership and generic rejection have separate recovery guidance`() {
        val status = SyncStatus()
        assertTrue(status.failureMessage(listOf(401)).contains("기기 인증"))
        assertTrue(status.failureMessage(listOf(403)).contains("기록을 만든 기기"))
        assertTrue(status.failureMessage(listOf(409)).contains("받아들이지"))
        assertTrue(status.failureMessage(listOf(null)).contains("받아들이지"))
    }
}
