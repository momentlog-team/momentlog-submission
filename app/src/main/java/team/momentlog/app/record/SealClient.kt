package team.momentlog.app.record

import org.json.JSONObject
import team.momentlog.core.RecordHasher
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * 원본 해시를 서버에 봉인한다(POST /api/v1/records/{id}/seal). 본문은 보내지 않고 해시만 보낸다.
 * 네트워크 작업이므로 메인 스레드에서 부르지 않는다.
 * TODO(앱): 실패하면 저장해 두었다가 통신이 돌아오면 다시 보내기.
 */
class SealClient(private val baseUrl: String) {

    fun seal(recordId: String, sha256: String, createdAtMillis: Long): Boolean = runCatching {
        val body = JSONObject()
            .put("sha256", sha256)
            .put("formatVersion", RecordHasher.FORMAT_VERSION)
            .put("deviceCreatedAt", Instant.ofEpochMilli(createdAtMillis).toString())
            .toString()
        val conn = (URL("$baseUrl/api/v1/records/$recordId/seal").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    }.getOrDefault(false)
}
