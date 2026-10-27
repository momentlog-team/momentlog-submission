package team.momentlog.app.core.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.momentlog.app.Constant
import team.momentlog.app.core.data.api.ApiInfo
import team.momentlog.app.core.data.api.RecordApi
import team.momentlog.app.core.data.model.record.VerifyRequest
import team.momentlog.app.core.data.model.record.VerifyResponse
import team.momentlog.app.core.error.Either
import team.momentlog.app.core.error.ErrorUtil
import team.momentlog.app.core.error.Failure
import team.momentlog.app.record.RecordStore
import team.momentlog.app.record.MockRecords
import team.momentlog.app.BuildConfig
import team.momentlog.core.RecordHasher

/**
 * 기록을 가져오는 진입점. 차 안 저장소([RecordStore])와 서버([RecordApi])를 합친다.
 * ViewModel은 이 Repository만 쓰고 RecordStore·RecordApi를 직접 부르지 않는다.
 *
 * 봉인(seal)은 기록이 완성되는 순간 [team.momentlog.app.RecorderService]가 부른다.
 */
class RecordRepository(context: Context) {

    private val store = RecordStore(context)
    private val api: RecordApi by lazy { ApiInfo.create(RecordApi::class.java) }
    private val mockMutex = Mutex()

    suspend fun ensureMockRecords() = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext
        mockMutex.withLock {
            val existing = store.list().map { it.record.id }.toSet()
            MockRecords.create(System.currentTimeMillis()).filterNot { it.id in existing }.forEach { record ->
                store.save(record, RecordHasher.sha256(record))
                store.retain(record.id)
            }
        }
    }

    /** 저장된 기록 목록. 최근 것부터. 보관 기간이 지난 기록은 먼저 지운다. */
    suspend fun getRecords(): List<RecordStore.Saved> = withContext(Dispatchers.IO) {
        store.purgeExpired(System.currentTimeMillis())
        store.list()
    }

    suspend fun getRecord(recordId: String): RecordStore.Saved? =
        getRecords().firstOrNull { it.record.id == recordId }

    /** 차 안 기록이 서버에 봉인된 원본과 같은지 대조한다. */
    suspend fun verify(recordId: String, sha256: String): Either<Failure, VerifyResponse> = try {
        val result = api.verify(recordId, VerifyRequest(sha256))
        Log.d(Constant.LOG_TAG, "verify $recordId = ${result.status}")
        Either.Right(result)
    } catch (error: Exception) {
        Either.Left(ErrorUtil.errorConvert(error))
    }

    suspend fun saveStatement(recordId: String, text: String, plate: String?, state: RecordStore.StatementState) =
        withContext(Dispatchers.IO) { store.saveStatement(recordId, text, plate, state) }

    /** 7일 자동 삭제에서 뺀다. */
    suspend fun retain(recordId: String) = withContext(Dispatchers.IO) { store.retain(recordId) }

    // TODO(앱): 삭제, 사고 정리 문서 요청(백엔드B 문서 API)
}
