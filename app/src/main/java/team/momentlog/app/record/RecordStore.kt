package team.momentlog.app.record

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import team.momentlog.core.IncidentRecord
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger
import team.momentlog.core.TurnSignal
import java.io.File

/**
 * 기록을 차 안(앱 내부 저장소)에 JSON으로 둔다. 위치·조작 내역은 서버로 보내지 않는다.
 * TODO(앱): 사고가 아닌 저장 건 7일 뒤 자동 삭제, 사용자가 보관을 선택한 건 유지.
 */
class RecordStore(context: Context) {
    private val dir = File(context.filesDir, "records").apply { mkdirs() }

    fun save(record: IncidentRecord, sha256: String) {
        val json = JSONObject()
            .put("id", record.id)
            .put("trigger", record.trigger.name)
            .put("triggerAt", record.triggerAtMillis)
            .put("sha256", sha256)
            .put("sealed", false)
            .put("snapshots", JSONArray().apply { record.snapshots.forEach { put(it.toJson()) } })
        File(dir, "${record.id}.json").writeText(json.toString())
    }

    fun markSealed(id: String) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return
        file.writeText(JSONObject(file.readText()).put("sealed", true).toString())
    }

    fun list(): List<Saved> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .map { load(JSONObject(it.readText())) }
        .sortedByDescending { it.record.triggerAtMillis }

    data class Saved(val record: IncidentRecord, val sha256: String, val sealed: Boolean)

    private fun load(o: JSONObject): Saved {
        val arr = o.getJSONArray("snapshots")
        val snaps = (0 until arr.length()).map { arr.getJSONObject(it).toSnapshot() }
        return Saved(
            record = IncidentRecord(o.getString("id"), Trigger.valueOf(o.getString("trigger")), o.getLong("triggerAt"), snaps),
            sha256 = o.getString("sha256"),
            sealed = o.optBoolean("sealed", false),
        )
    }

    private fun Snapshot.toJson() = JSONObject().apply {
        put("t", atMillis)
        speedKph?.let { put("speed", it.toDouble()) }
        brake?.let { put("brake", it) }
        turn?.let { put("turn", it.name) }
        steeringDeg?.let { put("steer", it.toDouble()) }
        lowBeam?.let { put("lowBeam", it) }
        highBeam?.let { put("highBeam", it) }
        wiperOn?.let { put("wiper", it) }
        latitude?.let { put("lat", it) }
        longitude?.let { put("lng", it) }
    }

    private fun JSONObject.toSnapshot() = Snapshot(
        atMillis = getLong("t"),
        speedKph = optNullable("speed") { getDouble(it).toFloat() },
        brake = optNullable("brake") { getBoolean(it) },
        turn = optNullable("turn") { TurnSignal.valueOf(getString(it)) },
        steeringDeg = optNullable("steer") { getDouble(it).toFloat() },
        lowBeam = optNullable("lowBeam") { getBoolean(it) },
        highBeam = optNullable("highBeam") { getBoolean(it) },
        wiperOn = optNullable("wiper") { getBoolean(it) },
        latitude = optNullable("lat") { getDouble(it) },
        longitude = optNullable("lng") { getDouble(it) },
    )

    private inline fun <T> JSONObject.optNullable(key: String, read: JSONObject.(String) -> T): T? =
        if (has(key) && !isNull(key)) read(key) else null
}
