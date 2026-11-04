package team.momentlog.app.record

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import team.momentlog.app.BuildConfig
import java.io.File
import kotlinx.coroutines.sync.withLock

/** 미봉인 파일 자체가 영속 대기열이다. 기록 서비스 시작·연결 복구·새 저장 때 재시도한다. */
class SealRetryQueue(context: Context, private val scope: CoroutineScope) {
    private val store = RecordStore(context)
    private val client = SealClient(BuildConfig.SEAL_SERVER, DeviceIdentity.get(context), DeviceIdentity.token(context))
    private val revocations = RevocationOutbox(File(context.filesDir, "share-revocations"))
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var changesJob: Job? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { request() }
    }

    fun start() {
        if (job != null) return
        connectivity.registerDefaultNetworkCallback(callback)
        changesJob = scope.launch { RevocationOutbox.changes.collect { request() } }
        job = scope.launch {
            var waitMillis = 5_000L
            while (isActive) {
                var retry = false
                try {
                    store.purgeExpired(System.currentTimeMillis())
                    // 삭제 의도 영속화 직후 프로세스가 종료됐어도 오프라인에서 삭제를 마무리한다.
                    for (id in revocations.pending()) store.delete(listOf(id))
                    RecordRemoteAccess.mutex.withLock {
                        if (connectivity.activeNetwork != null) {
                            for (saved in store.list().filter { !it.sealed && !it.sealRejected }) {
                                when (client.seal(saved.record.id, saved.sha256, saved.record.triggerAtMillis)) {
                                    SealClient.Result.SEALED -> store.markSealed(saved.record.id)
                                    SealClient.Result.REJECTED -> {
                                        store.markSealRejected(saved.record.id)
                                        Log.w("Momentlog", "Seal rejected; automatic retry stopped for ${saved.record.id}")
                                    }
                                    SealClient.Result.RETRY -> { retry = true; break }
                                }
                            }
                            // 봉인과 철회를 한 작업에서 직렬 처리해 삭제 중 진행하던 봉인도 뒤에서 철회한다.
                            for (id in revocations.pending()) {
                                // 삭제 의도 영속화 직후 프로세스가 종료된 경우도 마무리한다.
                                store.delete(listOf(id))
                                when (client.revoke(id)) {
                                    SealClient.Result.SEALED -> revocations.complete(id)
                                    SealClient.Result.REJECTED -> {
                                        revocations.reject(id)
                                        Log.w("Momentlog", "Share revocation rejected; authentication or ownership must be checked for $id")
                                    }
                                    SealClient.Result.RETRY -> retry = true
                                }
                            }
                        }
                    }
                } catch (error: java.io.IOException) {
                    retry = true
                    Log.w("Momentlog", "Seal queue could not read local records", error)
                }
                if (retry) {
                    withTimeoutOrNull(waitMillis) { wake.receive() }
                    waitMillis = (waitMillis * 2).coerceAtMost(300_000L)
                } else {
                    waitMillis = 5_000L
                    wake.receive()
                }
            }
        }
        request()
    }

    fun request() { wake.trySend(Unit) }

    fun stop() {
        connectivity.unregisterNetworkCallback(callback)
        job?.cancel()
        changesJob?.cancel()
        job = null
    }
}
