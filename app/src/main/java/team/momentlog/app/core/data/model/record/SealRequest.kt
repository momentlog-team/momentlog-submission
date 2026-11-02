package team.momentlog.app.core.data.model.record

/** 원본 해시 봉인 요청. `deviceCreatedAt`은 ISO-8601 문자열. */
data class SealRequest(
    val sha256: String,
    val formatVersion: Int,
    val deviceCreatedAt: String,
    val deviceId: String,
)
