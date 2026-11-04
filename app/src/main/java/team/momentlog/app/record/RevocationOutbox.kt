package team.momentlog.app.record

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 기록 삭제 전에 영속화한다. 요청에는 기록 ID만 남기고 다음 앱 실행 때도 재시도한다. */
internal class RevocationOutbox(private val dir: File) {
    fun enqueue(id: String) = synchronized(lock) {
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        dir.mkdirs()
        val target = File(dir, "$id.pending")
        if (!target.exists() && !File(dir, "$id.rejected").exists()) {
            val temp = File(dir, "$id.tmp")
            FileOutputStream(temp).use { it.write(id.toByteArray()); it.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            _changes.value += 1
        }
    }

    fun pending(): List<String> = synchronized(lock) {
        dir.listFiles().orEmpty().filter { it.extension == "pending" }.map { it.nameWithoutExtension }
    }

    fun complete(id: String) = synchronized(lock) {
        val file = File(dir, "$id.pending")
        if (file.exists() && !file.delete()) throw IOException("공유 철회 완료 상태를 저장하지 못했어요.")
    }

    fun reject(id: String) = synchronized(lock) {
        Files.move(File(dir, "$id.pending").toPath(), File(dir, "$id.rejected").toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }

    companion object {
        private val lock = Any()
        private val _changes = MutableStateFlow(0L)
        val changes: StateFlow<Long> = _changes
    }
}
