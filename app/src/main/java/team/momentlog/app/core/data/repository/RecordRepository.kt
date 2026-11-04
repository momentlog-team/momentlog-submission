package team.momentlog.app.core.data.repository

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import team.momentlog.app.core.data.model.record.DocumentRequest
import team.momentlog.app.core.data.model.record.DocumentResponse
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import team.momentlog.app.record.RecordRemoteAccess
import team.momentlog.app.record.DeviceIdentity
import team.momentlog.app.record.SealClient
import team.momentlog.app.BuildConfig
import team.momentlog.app.Constant
import team.momentlog.app.core.data.api.ApiInfo
import team.momentlog.app.core.data.api.RecordApi
import team.momentlog.app.core.data.model.record.VerifyRequest
import team.momentlog.app.core.data.model.record.VerifyResponse
import team.momentlog.app.core.error.Either
import team.momentlog.app.core.error.ErrorUtil
import team.momentlog.app.core.error.Failure
import team.momentlog.app.record.RecordStore
import team.momentlog.core.RecordHasher

/**
 * 기록을 가져오는 진입점. 차 안 저장소([RecordStore])와 서버([RecordApi])를 합친다.
 * ViewModel은 이 Repository만 쓰고 RecordStore·RecordApi를 직접 부르지 않는다.
 *
 * 봉인(seal)은 기록이 완성되는 순간 [team.momentlog.app.RecorderService]가 부른다.
 */
class RecordRepository(context: Context) {

    val changes = RecordStore.changes
    private val store = RecordStore(context)
    private val sealClient by lazy {
        SealClient(BuildConfig.SEAL_SERVER, DeviceIdentity.get(context.applicationContext), DeviceIdentity.token(context.applicationContext))
    }
    private val api: RecordApi by lazy { ApiInfo.create(RecordApi::class.java) }
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

    /** QR 화면을 열었을 때만 저장된 원본과 진술을 서버에 공유한다. */
    suspend fun createDocument(recordId: String): Either<Failure, DocumentResponse> = withContext(Dispatchers.IO) {
        RecordRemoteAccess.mutex.withLock {
            try {
                val saved = getRecord(recordId)
                    ?: return@withContext Either.Left(Failure(message = "기록을 찾을 수 없어요."))
                // 새 기록 직후 QR을 열어도 서버 봉인보다 공유 생성이 먼저 호출되지 않게 한다.
                if (!saved.sealed) {
                    if (saved.sealRejected) return@withContext Either.Left(Failure(message = "원본 봉인이 거부되어 공유할 수 없어요. 기기 인증과 기록을 확인해 주세요."))
                    when (sealClient.seal(recordId, saved.sha256, saved.record.triggerAtMillis)) {
                        SealClient.Result.SEALED -> store.markSealed(recordId)
                        SealClient.Result.RETRY -> return@withContext Either.Left(Failure(message = "원본 봉인 대기 중이에요. 연결을 확인한 뒤 다시 받아 주세요."))
                        SealClient.Result.REJECTED -> {
                            store.markSealRejected(recordId)
                            return@withContext Either.Left(Failure(message = "원본 봉인이 거부되어 공유할 수 없어요. 기기 인증과 기록을 확인해 주세요."))
                        }
                    }
                }
                val statement = if (saved.statement.isNullOrBlank() && saved.plate.isNullOrBlank()) null else
                    JSONObject().put("text", saved.statement ?: "")
                        .put("plate", saved.plate ?: JSONObject.NULL).toString()
                Either.Right(api.createDocument(recordId, DocumentRequest(
                    body = RecordHasher.canonical(saved.record),
                    statementJson = statement,
                )))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Either.Left(ErrorUtil.errorConvert(error))
            }
        }
    }

    suspend fun delete(recordId: String) = withContext(Dispatchers.IO) { store.delete(listOf(recordId)) }
}
