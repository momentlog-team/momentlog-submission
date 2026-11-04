package team.momentlog.app.record

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class StatementWriteOrderTest {
    @Test fun `saving while screen closes cannot downgrade a Korean statement to draft`() = runBlocking {
        val writes = mutableListOf<String>()
        val order = StatementWriteOrder()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val save = launch {
            order.write("record-1", 1, true) {
                started.complete(Unit)
                finish.await()
                writes += "완료: 뒤에서 받았어요. 12가 3456"
            }
        }
        started.await()
        val leaving = launch { order.write("record-1", 1, false) { writes += "초안" } }
        finish.complete(Unit)
        save.join()
        leaving.join()
        assertEquals(listOf("완료: 뒤에서 받았어요. 12가 3456"), writes)
    }

    @Test fun `new edits are saved but an older delayed draft cannot overwrite them`() = runBlocking {
        val writes = mutableListOf<String>()
        val order = StatementWriteOrder()
        order.write("record-1", 1, true) { writes += "완료" }
        order.write("record-1", 2, false) { writes += "새 진술" }
        order.write("record-1", 1, false) { writes += "이전 초안" }
        assertEquals(listOf("완료", "새 진술"), writes)
    }

    @Test fun `failed save can be retried and does not discard the draft`() = runBlocking {
        val order = StatementWriteOrder()
        var state = ""
        try {
            order.write("record-1", 1, true) { throw IOException("disk full") }
            fail<Unit>("must fail")
        } catch (_: IOException) { }
        order.write("record-1", 1, false) { state = "초안" }
        assertEquals("초안", state)
        order.write("record-1", 1, true) { state = "완료" }
        assertEquals("완료", state)
    }
}
