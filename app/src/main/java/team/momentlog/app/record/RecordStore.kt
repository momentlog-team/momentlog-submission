package team.momentlog.app.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.AtomicFile
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
 * 충격·에어백이 감지됐거나 진술을 남긴 기록, 사용자가 보관을 고른 기록은 남긴다.
 * 나머지는 저장 후 [Constant.RETENTION_DAYS]일 뒤 [purgeExpired]가 지운다.
 * 진술은 센서 기록(snapshots)과 다른 필드에 두어, 해시(원본 지문) 대상에 섞이지 않게 한다.
 */
class RecordStore(context: Context) {
    private val dir = File(context.filesDir, "records").apply { mkdirs() }

    init {
        // 이전 Debug 버전이 자동 삽입한 예시 파일만 정리한다. 실제 기록은 UUID를 쓴다.
        synchronized(lock) {
            recordFiles().filter { it.name.startsWith("mock-") }.forEach { AtomicFile(it).delete() }
        }
    }

    fun save(record: IncidentRecord, sha256: String, savedWhileParked: Boolean = false) = synchronized(lock) {
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
        write(File(dir, "${record.id}.json"), json)
    }

    fun markSealed(id: String) = update(id) { put("sealed", true) }

    fun saveStatement(id: String, text: String, plate: String?, state: StatementState) = update(id) {
        put("statement", text)
        put("plate", plate ?: JSONObject.NULL)
        put("statementState", state.name)
    }

    fun markSealRejected(id: String) = update(id) { put("sealRejected", true) }

    fun retain(id: String) = update(id) { put("retained", true) }

    /** 다른 기록에 합쳐진 앞선 기록을 지운다. 합친 기록이 그 구간과 감지를 그대로 담고 있다. */
    fun delete(ids: List<String>) = synchronized(lock) { ids.forEach { AtomicFile(File(dir, "$it.json")).delete() } }

    /** 보관 대상이 아닌데 보관 기간이 지난 기록을 지운다. */
    fun purgeExpired(nowMillis: Long) = synchronized(lock) {
        recordFiles().forEach { file ->
            val saved = load(JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8)))
            if (!saved.isKept && nowMillis >= saved.deleteAtMillis) AtomicFile(file).delete()
        }
    }

    private fun update(id: String, change: JSONObject.() -> Unit) = synchronized(lock) {
        val file = File(dir, "$id.json")
        if (!file.exists() && !File(dir, "$id.json.bak").exists()) return@synchronized
        write(file, JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8)).apply(change))
    }

    fun list(): List<Saved> = synchronized(lock) { recordFiles()
        .map { load(JSONObject(AtomicFile(it).readFully().toString(Charsets.UTF_8))) }
        .sortedByDescending { it.record.triggerAtMillis } }

    enum class StatementState { NONE, LATER, WRITING, DONE }

    data class Saved(
        val record: IncidentRecord,
        val sha256: String,
        val sealed: Boolean,
        val sealRejected: Boolean = false,
        val savedWhileParked: Boolean = false,
        val statement: String? = null,
        val plate: String? = null,
        val statementState: StatementState = StatementState.NONE,
        val retained: Boolean = false,
    ) {
        /** 자동 보관 또는 사용자가 보관한 기록. */
        val isKept: Boolean
            get() = retained || record.events.any { it.trigger.isCollision } || statementState == StatementState.DONE

        val deleteAtMillis: Long get() = record.triggerAtMillis + Constant.RETENTION_DAYS * 24 * 60 * 60 * 1000L
    }

    private fun recordFiles(): List<File> = dir.listFiles().orEmpty()
        .filter { it.extension == "json" || it.name.endsWith(".json.bak") }
        .map { if (it.name.endsWith(".bak")) File(dir, it.name.removeSuffix(".bak")) else it }
        .distinctBy { it.name }

    private fun write(file: File, json: JSONObject) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            _changes.value += 1
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    companion object {
        private val lock = Any()
        private val _changes = MutableStateFlow(0L)
        val changes: StateFlow<Long> = _changes
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
            sealRejected = o.optBoolean("sealRejected", false),
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
