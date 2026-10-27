package team.momentlog.app.record

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import team.momentlog.app.Constant
import team.momentlog.core.IncidentEvent
import team.momentlog.core.IncidentRecord
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger
import team.momentlog.core.TurnSignal
import java.io.File

/**
 * 기록을 차 안(앱 내부 저장소)에 JSON으로 둔다. 위치·조작 내역은 서버로 보내지 않는다.
 *
 * 에어백(충격)이 감지됐거나 진술을 남긴 기록, 사용자가 보관을 고른 기록은 남긴다.
 * 나머지는 저장 후 [Constant.RETENTION_DAYS]일 뒤 [purgeExpired]가 지운다.
 * 진술은 센서 기록(snapshots)과 다른 필드에 두어, 해시(원본 지문) 대상에 섞이지 않게 한다.
 */
class RecordStore(context: Context) {
    private val dir = File(context.filesDir, "records").apply { mkdirs() }

    fun save(record: IncidentRecord, sha256: String, savedWhileParked: Boolean = false) {
        val json = JSONObject()
            .put("id", record.id)
            .put("trigger", record.trigger.name)
            .put("triggerAt", record.triggerAtMillis)
            .put("sha256", sha256)
            .put("sealed", false)
            .put("parked", savedWhileParked)
            .put("events", JSONArray().apply { record.events.forEach { put(JSONObject().put("trigger", it.trigger.name).put("t", it.atMillis)) } })
            .put("merged", JSONArray(record.mergedRecordIds))
            .put("snapshots", JSONArray().apply { record.snapshots.forEach { put(it.toJson()) } })
        File(dir, "${record.id}.json").writeText(json.toString())
    }

    fun markSealed(id: String) = update(id) { put("sealed", true) }

    fun saveStatement(id: String, text: String, plate: String?, state: StatementState) = update(id) {
        put("statement", text)
        put("plate", plate ?: JSONObject.NULL)
        put("statementState", state.name)
    }

    fun retain(id: String) = update(id) { put("retained", true) }

    /** 다른 기록에 합쳐진 앞선 기록을 지운다. 합친 기록이 그 구간과 감지를 그대로 담고 있다. */
    fun delete(ids: List<String>) = ids.forEach { File(dir, "$it.json").delete() }

    /** 보관 대상이 아닌데 보관 기간이 지난 기록을 지운다. */
    fun purgeExpired(nowMillis: Long) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty().forEach { file ->
            val saved = load(JSONObject(file.readText()))
            if (!saved.isKept && nowMillis >= saved.deleteAtMillis) file.delete()
        }
    }

    private fun update(id: String, change: JSONObject.() -> Unit) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return
        file.writeText(JSONObject(file.readText()).apply(change).toString())
    }

    fun list(): List<Saved> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .map { load(JSONObject(it.readText())) }
        .sortedByDescending { it.record.triggerAtMillis }

    enum class StatementState { NONE, LATER, WRITING, DONE }

    data class Saved(
        val record: IncidentRecord,
        val sha256: String,
        val sealed: Boolean,
        val savedWhileParked: Boolean = false,
        val statement: String? = null,
        val plate: String? = null,
        val statementState: StatementState = StatementState.NONE,
        val retained: Boolean = false,
    ) {
        /** 자동 보관 또는 사용자가 보관한 기록. */
        val isKept: Boolean
            get() = retained || record.trigger == Trigger.AIRBAG || statementState == StatementState.DONE

        val deleteAtMillis: Long get() = record.triggerAtMillis + Constant.RETENTION_DAYS * 24 * 60 * 60 * 1000L
    }

    private fun load(o: JSONObject): Saved {
        val arr = o.getJSONArray("snapshots")
        val snaps = (0 until arr.length()).map { arr.getJSONObject(it).toSnapshot() }
        val trigger = Trigger.valueOf(o.getString("trigger"))
        val triggerAt = o.getLong("triggerAt")
        val events = o.optJSONArray("events")?.let { e ->
            (0 until e.length()).map { e.getJSONObject(it).let { j -> IncidentEvent(Trigger.valueOf(j.getString("trigger")), j.getLong("t")) } }
        } ?: listOf(IncidentEvent(trigger, triggerAt))
        val merged = o.optJSONArray("merged")?.let { m -> (0 until m.length()).map { m.getString(it) } }.orEmpty()
        return Saved(
            record = IncidentRecord(o.getString("id"), trigger, triggerAt, snaps, events, merged),
            sha256 = o.getString("sha256"),
            sealed = o.optBoolean("sealed", false),
            savedWhileParked = o.optBoolean("parked", false),
            statement = o.optNullable("statement") { getString(it) },
            plate = o.optNullable("plate") { getString(it) },
            statementState = o.optNullable("statementState") { StatementState.valueOf(getString(it)) } ?: StatementState.NONE,
            retained = o.optBoolean("retained", false),
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
