package team.momentlog.app.record

import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*

class RevocationOutboxTest {
    @TempDir lateinit var dir: File

    @Test fun `deletion intent survives restart and duplicate requests`() {
        RevocationOutbox(dir).enqueue("record-1")
        val restarted = RevocationOutbox(dir)
        restarted.enqueue("record-1")
        assertEquals(listOf("record-1"), restarted.pending())
        assertEquals("record-1", File(dir, "record-1.pending").readText())
        restarted.complete("record-1")
        assertTrue(RevocationOutbox(dir).pending().isEmpty())
    }

    @Test fun `authentication rejection persists and is not automatically retried`() {
        val outbox = RevocationOutbox(dir)
        outbox.enqueue("record-1")
        outbox.reject("record-1")
        RevocationOutbox(dir).enqueue("record-1")
        assertTrue(outbox.pending().isEmpty())
        assertTrue(File(dir, "record-1.rejected").exists())
    }

    @Test fun `incomplete writes and path traversal are excluded`() {
        File(dir, "record-1.tmp").writeText("record-1")
        val outbox = RevocationOutbox(dir)
        assertTrue(outbox.pending().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { outbox.enqueue("../record") }
        outbox.enqueue("record-1")
        assertEquals(listOf("record-1"), outbox.pending())
    }
}
