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
import team.momentlog.app.voice.GleoRecordAction
import team.momentlog.core.CaptureWindow
import team.momentlog.core.IncidentEvent
import team.momentlog.core.IncidentRecord
import team.momentlog.core.IncidentRecorder
import team.momentlog.core.RecordHasher
import team.momentlog.core.SignalState
import team.momentlog.core.Snapshot
import team.momentlog.core.Trigger

/**
 * 화면이 가려져도 최근 3분을 계속 들고 있는 포그라운드 서비스.
 * 100ms마다 현재 신호 상태를 스냅샷으로 찍어 [IncidentRecorder]에 넣고, 기록이 완성되면 저장·봉인한다.
 *
 * 음성 비서의 기록 요청도 UI 버튼과 같은 경로로 처리한다.
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
            val snapshot = state.snapshot(System.currentTimeMillis())
            val wasCapturing = recorder.isCapturing
            val completed = recorder.onSnapshot(snapshot)
            if (!wasCapturing && recorder.isCapturing) onCaptureStarted(Trigger.HARD_STOP, snapshot)
            if (recorder.isCapturing) _capture.value?.let { c -> if (c.events != recorder.pendingEvents) _capture.value = c.copy(events = recorder.pendingEvents) }
            completed?.let(::onRecordCompleted)
            _live.value = snapshot
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = RecordStore(this)
        sealClient = SealClient(BuildConfig.SEAL_SERVER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(GleoRecordAction.EXTRA_FROM_GLEO, false) == true && !GleoRecordAction.ENABLED) {
            if (source == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        return try {
            handleStartCommand(intent)
        } catch (error: Exception) {
            if (intent?.getBooleanExtra(GleoRecordAction.EXTRA_FROM_GLEO, false) != true) throw error
            GleoRecordAction.respond(this, success = false,
                message = "Recording could not be initialized. Open Momentlog and check the recording permissions.")
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    private fun handleStartCommand(intent: Intent?): Int {
        val recordNowRequested = intent?.action == ACTION_RECORD_NOW
        if (recordNowRequested && source != null) {
            recordRequested(intent, hasRecentBuffer = true)
            return START_STICKY
        }
        val replay = intent?.getBooleanExtra(EXTRA_REPLAY, false) == true
        startForegroundCompat(if (replay) "시연 데이터로 기록 중" else "최근 3분을 기록하고 있어요")

        source?.stop()
        source = (if (replay) ReplaySource() else VehicleSignalClient(this)).also { s ->
            s.onAirbagDeployed = { trigger(Trigger.AIRBAG) }
            s.start(state)
        }
        _status.value = if (replay) Status.REPLAYING else Status.RECORDING
        handler.removeCallbacks(tick)
        handler.post(tick)
        // 꺼져 있을 때 "기록해"가 오면 켜자마자 기록한다(버퍼가 비어 있어 직전 기록은 없다).
        if (recordNowRequested) recordRequested(intent, hasRecentBuffer = false)
        return START_STICKY
    }

    private fun recordNow() = trigger(Trigger.VOICE)

    private fun recordRequested(intent: Intent?, hasRecentBuffer: Boolean) {
        val fromGleo = intent?.getBooleanExtra(GleoRecordAction.EXTRA_FROM_GLEO, false) == true
        try {
            recordNow()
            if (fromGleo) {
                GleoRecordAction.respond(this, success = true, message = if (hasRecentBuffer) {
                    "The capture request has been accepted. Available recent vehicle signals will be included, and the record will be saved when capture finishes."
                } else {
                    "Recording has just started and the capture request has been accepted. Earlier vehicle signals are unavailable."
                })
            }
        } catch (error: Exception) {
            if (!fromGleo) throw error
            GleoRecordAction.respond(this, success = false, message = "The capture request could not be processed. Please try again.")
        }
    }

    /** 정차 중 "기록해"는 뒤 구간 없이 바로 저장한다. */
    private fun trigger(trigger: Trigger) {
        val now = state.snapshot(System.currentTimeMillis())
        val parked = isParked(now)
        val wasCapturing = recorder.isCapturing
        if (trigger == Trigger.VOICE && parked && !wasCapturing) {
            recorder.trigger(trigger, now.atMillis, postMillis = 0)
        } else {
            recorder.trigger(trigger, now.atMillis)
        }
        if (wasCapturing) {
            _capture.value = _capture.value?.copy(events = recorder.pendingEvents)
        } else {
            onCaptureStarted(trigger, now)
        }
    }

    private fun onCaptureStarted(trigger: Trigger, at: Snapshot) {
        _capture.value = Capture(trigger, at.atMillis, parked = isParked(at), events = recorder.pendingEvents)
    }

    private fun isParked(at: Snapshot) = (at.speedKph ?: 0f) < CaptureWindow.STOPPED_KPH

    private fun onRecordCompleted(record: IncidentRecord) {
        val sha = RecordHasher.sha256(record)
        val parked = _capture.value?.parked == true
        val captureAtMillis = _capture.value?.atMillis
        io.launch {
            store.save(record, sha, savedWhileParked = parked)
            // 진술 저장이 파일 생성보다 먼저 실행되지 않도록, 파일 저장 후 완료를 알린다.
            _capture.value = _capture.value?.let { capture ->
                if (capture.atMillis == captureAtMillis) capture.copy(recordId = record.id) else capture
            }
            store.delete(record.mergedRecordIds)
            if (sealClient.seal(record.id, sha, record.triggerAtMillis)) store.markSealed(record.id)
            _lastSavedId.value = record.id
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        source?.stop()
        io.cancel()
        _status.value = Status.STOPPED
        _live.value = null
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

    /**
     * 진행 중이거나 막 끝난 저장. [recordId]는 저장이 끝나면 채워진다.
     */
    data class Capture(
        val trigger: Trigger,
        val atMillis: Long,
        val parked: Boolean,
        val events: List<IncidentEvent> = emptyList(),
        val recordId: String? = null,
    )

    companion object {
        private const val TICK_MS = 100L
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_REPLAY = "replay"
        private const val ACTION_RECORD_NOW = "team.momentlog.app.RECORD_NOW"

        private val _status = MutableStateFlow(Status.STOPPED)
        val status: StateFlow<Status> = _status
        private val _lastSavedId = MutableStateFlow<String?>(null)
        val lastSavedId: StateFlow<String?> = _lastSavedId

        /** 100ms마다 갱신되는 현재 신호. */
        private val _live = MutableStateFlow<Snapshot?>(null)
        val live: StateFlow<Snapshot?> = _live

        private val _capture = MutableStateFlow<Capture?>(null)
        val capture: StateFlow<Capture?> = _capture

        fun clearCapture() {
            _capture.value = null
        }

        fun recordNow(context: Context, fromGleo: Boolean = false) {
            context.startForegroundService(Intent(context, RecorderService::class.java).setAction(ACTION_RECORD_NOW)
                .putExtra(GleoRecordAction.EXTRA_FROM_GLEO, fromGleo))
        }

        fun start(context: Context, replay: Boolean) {
            context.startForegroundService(Intent(context, RecorderService::class.java).putExtra(EXTRA_REPLAY, replay))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }
}
