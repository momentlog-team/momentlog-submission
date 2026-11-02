package team.momentlog.app.record

import com.google.gson.Gson
import team.momentlog.app.core.data.model.record.SealRequest
import team.momentlog.core.RecordHasher
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * 원본 해시를 서버에 봉인한다(POST /api/v1/records/{id}/seal). 본문은 보내지 않고 해시만 보낸다.
 * 네트워크 작업이므로 메인 스레드에서 부르지 않는다.
 */
class SealClient(private val baseUrl: String, private val deviceId: String) {

    fun seal(recordId: String, sha256: String, createdAtMillis: Long): Result = runCatching {
        val body = Gson().toJson(SealRequest(
            sha256 = sha256,
            formatVersion = RecordHasher.FORMAT_VERSION,
            deviceCreatedAt = Instant.ofEpochMilli(createdAtMillis).toString(),
            deviceId = deviceId,
        ))
        val conn = (URL("$baseUrl/api/v1/records/$recordId/seal").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> Result.SEALED
                408, 429, in 500..599 -> Result.RETRY
                else -> Result.REJECTED
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(Result.RETRY)

    enum class Result { SEALED, RETRY, REJECTED }
}
