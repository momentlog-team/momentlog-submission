package team.momentlog.app.record

import android.content.Context
import java.util.UUID

/** 차량 식별정보 대신 설치 단위 난수만 사용한다. 앱 데이터 삭제 시 새로 발급된다. */
object DeviceIdentity {
    @Synchronized
    fun get(context: Context): String {
        val preferences = context.applicationContext.getSharedPreferences("installation", Context.MODE_PRIVATE)
        return preferences.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString("deviceId", it).commit()) { "기기 ID를 저장하지 못했어요." }
        }
    }
}
