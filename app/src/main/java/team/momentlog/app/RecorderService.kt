package team.momentlog.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import team.momentlog.app.record.RecordStore
import team.momentlog.app.record.SealClient
import team.momentlog.app.signal.VehicleSignalClient
import team.momentlog.app.signal.ReplaySource
import team.momentlog.app.signal.SignalSource
import team.momentlog.core.IncidentRecord
import team.momentlog.core.IncidentRecorder
import team.momentlog.core.RecordHasher
import team.momentlog.core.SignalState
import team.momentlog.core.Trigger

/**
 * 화면이 가려져도 최근 30초를 계속 들고 있는 포그라운드 서비스.
 * 100ms마다 현재 신호 상태를 스냅샷으로 찍어 [IncidentRecorder]에 넣고, 기록이 완성되면 저장·봉인한다.
 *
 * TODO(앱): 정차 후 음성 진술(TTS 질문 → STT), "기록해" 음성 트리거, CarUxRestrictions 대응.
 */
class RecorderService : Service() {

    private val state = SignalState()
    private val recorder = IncidentRecorder()
    private val handler = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: RecordStore
    private lateinit var sealClient: SealClient
    private var source: SignalSource? = null

    private val tick = object : Runnable {
        override fun run() {
            recorder.onSnapshot(state.snapshot(System.currentTimeMillis()))?.let(::onRecordCompleted)
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = RecordStore(this)
        sealClient = SealClient(BuildConfig.SEAL_SERVER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val replay = intent?.getBooleanExtra(EXTRA_REPLAY, false) == true
        startForegroundCompat(if (replay) "시연 데이터로 기록 중" else "최근 30초를 기록하고 있어요")

        source?.stop()
        source = (if (replay) ReplaySource() else VehicleSignalClient(this)).also { s ->
            s.onAirbagDeployed = { recorder.trigger(Trigger.AIRBAG, System.currentTimeMillis()) }
            s.start(state)
        }
        _status.value = if (replay) Status.REPLAYING else Status.RECORDING
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun onRecordCompleted(record: IncidentRecord) {
        val sha = RecordHasher.sha256(record)
        io.launch {
            store.save(record, sha)
            if (sealClient.seal(record.id, sha, record.triggerAtMillis)) store.markSealed(record.id)
            _lastSavedId.value = record.id
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        source?.stop()
        io.cancel()
        _status.value = Status.STOPPED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "순간기록", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("순간기록")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    enum class Status { STOPPED, RECORDING, REPLAYING }

    companion object {
        private const val TICK_MS = 100L
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_REPLAY = "replay"

        private val _status = MutableStateFlow(Status.STOPPED)
        val status: StateFlow<Status> = _status
        private val _lastSavedId = MutableStateFlow<String?>(null)
        val lastSavedId: StateFlow<String?> = _lastSavedId

        fun start(context: Context, replay: Boolean) {
            context.startForegroundService(Intent(context, RecorderService::class.java).putExtra(EXTRA_REPLAY, replay))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }
}
