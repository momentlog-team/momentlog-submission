package team.momentlog.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import team.momentlog.app.record.SealRetryQueue

/** 기록 서비스가 꺼져 있어도 앱 실행·네트워크 복구 시 봉인/철회를 재개한다. */
class MomentlogApplication : Application() {
    lateinit var syncQueue: SealRetryQueue
        private set

    override fun onCreate() {
        super.onCreate()
        syncQueue = SealRetryQueue(this, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { it.start() }
    }
}
