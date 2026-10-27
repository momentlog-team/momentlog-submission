package team.momentlog.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import team.momentlog.app.Constant
import team.momentlog.app.RecorderService

/** Gleo의 component=service 호출은 Android 서비스 바인딩이 아니라 Broadcast다. */
class GleoRecordReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!GleoRecordAction.ENABLED || intent.action != GleoRecordAction.SAVE_RECENT_DRIVE) return
        try {
            RecorderService.recordNow(context, fromGleo = true)
        } catch (error: Exception) {
            Log.w(Constant.LOG_TAG, "Gleo record request could not start recording", error)
            GleoRecordAction.respond(context, success = false,
                message = "Recording could not start in the background. Open Momentlog and try again.")
        }
    }
}

object GleoRecordAction {
    const val ENABLED = false
    const val SAVE_RECENT_DRIVE = "SAVE_RECENT_DRIVE"
    const val EXTRA_FROM_GLEO = "team.momentlog.app.FROM_GLEO"
    private const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    /** 7초 응답 제한 내에 요청 접수만 알린다. 파일 저장 완료로 오해할 문구는 사용하지 않는다. */
    fun respond(context: Context, success: Boolean, message: String) {
        val response = JSONObject().apply {
            put("functionName", SAVE_RECENT_DRIVE)
            put("status", if (success) "success" else "fail")
            put("message", message)
            put("mode", "voice")
        }
        runCatching {
            context.sendBroadcast(Intent(RESPONSE).apply {
                putExtra("packageName", context.packageName)
                putExtra("response", response.toString())
            })
            Log.d(Constant.LOG_TAG, "Gleo SAVE_RECENT_DRIVE response: ${if (success) "accepted" else "failed"}")
        }.onFailure { Log.w(Constant.LOG_TAG, "Gleo response could not be delivered", it) }
    }
}
