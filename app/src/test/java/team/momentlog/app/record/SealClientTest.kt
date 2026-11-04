package team.momentlog.app.record

import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SealClientTest {
    @Test fun `terminal response retains authentication and ownership status for user guidance`() {
        MockWebServer().use { server ->
            server.start()
            val client = SealClient(server.url("/").toString().trimEnd('/'), "installation-1", "test-device-token")
            for (status in listOf(401, 403, 409, 503)) {
                server.enqueue(MockResponse().setResponseCode(status))
                val seal = client.sealResponse("record-1", "a".repeat(64), 0)
                assertEquals(status, seal.status)
                assertEquals(if (status == 503) SealClient.Result.RETRY else SealClient.Result.REJECTED, seal.result)
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(status, client.revokeResponse("record-1").status)
            }
        }
    }

    @Test
    fun `revocation sends device authentication and retries only recoverable failures`() {
        MockWebServer().use { server ->
            server.start()
            val client = SealClient(server.url("/").toString().trimEnd('/'), "installation-1", "test-device-token")
            for ((status, expected) in listOf(
                204 to SealClient.Result.SEALED, 404 to SealClient.Result.SEALED,
                401 to SealClient.Result.REJECTED, 403 to SealClient.Result.REJECTED,
                408 to SealClient.Result.RETRY, 429 to SealClient.Result.RETRY, 503 to SealClient.Result.RETRY,
            )) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(expected, client.revoke("record-1"))
                val request = server.takeRequest()
                assertEquals("DELETE", request.method)
                assertEquals("/api/v1/records/record-1/documents", request.path)
                assertEquals("installation-1", request.getHeader("X-Device-Id"))
                assertEquals("test-device-token", request.getHeader("X-Device-Token"))
                assertEquals(0L, request.bodySize)
            }
        }
    }

    @Test
    fun `retry preserves installation ID and original timestamp and sends no sensor body`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()
            val client = SealClient(server.url("/").toString().trimEnd('/'), "installation-1", "test-device-token")
            assertEquals(SealClient.Result.RETRY, client.seal("record-1", "a".repeat(64), 0))
            assertEquals(SealClient.Result.SEALED, client.seal("record-1", "a".repeat(64), 0))
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("test-device-token", first.getHeader("X-Device-Token"))
            assertEquals(first.getHeader("X-Device-Token"), second.getHeader("X-Device-Token"))
            assertEquals("POST", first.method)
            assertEquals("/api/v1/records/record-1/seal", first.path)
            val payload = first.body.readUtf8()
            assertEquals(payload, second.body.readUtf8())
            val body = JsonParser.parseString(payload).asJsonObject
            assertEquals("installation-1", body["deviceId"].asString)
            assertEquals("1970-01-01T00:00:00Z", body["deviceCreatedAt"].asString)
            assertEquals("a".repeat(64), body["sha256"].asString)
            assertEquals(setOf("deviceId", "sha256", "formatVersion", "deviceCreatedAt"), body.keySet())
        }
    }

    @Test
    fun `conflict is terminal while rate limits and server errors retry`() {
        MockWebServer().use { server ->
            server.start()
            val client = SealClient(server.url("/").toString().trimEnd('/'), "installation-1", "test-device-token")
            for (status in listOf(400, 401, 403, 404, 409)) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(SealClient.Result.REJECTED, client.seal("record-1", "a".repeat(64), 0))
            }
            for (status in listOf(408, 429, 500, 503)) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(SealClient.Result.RETRY, client.seal("record-1", "a".repeat(64), 0))
            }
            server.shutdown()
            assertEquals(SealClient.Result.RETRY, client.seal("record-1", "a".repeat(64), 0))
        }
    }
}
