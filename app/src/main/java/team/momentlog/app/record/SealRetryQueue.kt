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

/** 미봉인 파일 자체가 영속 대기열이다. 기록 서비스 시작·연결 복구·새 저장 때 재시도한다. */
class SealRetryQueue(context: Context, private val scope: CoroutineScope) {
    private val store = RecordStore(context)
    private val client = SealClient(BuildConfig.SEAL_SERVER, DeviceIdentity.get(context))
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { request() }
    }

    fun start() {
        connectivity.registerDefaultNetworkCallback(callback)
        job = scope.launch {
            var waitMillis = 5_000L
            while (isActive) {
                var retry = false
                try {
                    if (connectivity.activeNetwork != null) {
                        // 기존 보관 정책을 그대로 적용하고, 시연용 mock 파일은 서버에 보내지 않는다.
                        store.purgeExpired(System.currentTimeMillis())
                        for (saved in store.list().filter { !it.sealed && !it.sealRejected && !it.record.id.startsWith("mock-") }) {
                            when (client.seal(saved.record.id, saved.sha256, saved.record.triggerAtMillis)) {
                                SealClient.Result.SEALED -> store.markSealed(saved.record.id)
                                SealClient.Result.REJECTED -> {
                                    store.markSealRejected(saved.record.id)
                                    Log.w("Momentlog", "Seal rejected; automatic retry stopped for ${saved.record.id}")
                                }
                                SealClient.Result.RETRY -> { retry = true; break }
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
    }
}
