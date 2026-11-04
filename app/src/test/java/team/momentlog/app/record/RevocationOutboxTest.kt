package team.momentlog.app.record

import java.io.File
import java.io.IOException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*

class RevocationOutboxTest {
    @TempDir lateinit var dir: File

    @Test fun `rejection cause survives restart and manual retry restores the same request`() {
        val outbox = RevocationOutbox(dir)
        outbox.enqueue("record-1")
        outbox.reject("record-1", 403)
        val restarted = RevocationOutbox(dir)
        assertEquals(listOf(403), restarted.rejectedStatuses())
        assertTrue(restarted.pending().isEmpty())
        restarted.retryRejected()
        assertEquals(listOf("record-1"), restarted.pending())
        assertTrue(restarted.rejectedStatuses().isEmpty())
    }

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

    @Test fun `completion write failure is recoverable and preserves pending intent`() {
        val blocked = File(dir, "record-1.pending").apply { mkdir() }
        File(blocked, "busy").writeText("busy")
        val outbox = RevocationOutbox(dir)
        assertThrows(IOException::class.java) { outbox.complete("record-1") }
        assertEquals(listOf("record-1"), outbox.pending())
    }
}
