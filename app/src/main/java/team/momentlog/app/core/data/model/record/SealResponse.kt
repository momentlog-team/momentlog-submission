package team.momentlog.app.core.data.model.record

/** 봉인 결과. `sealedAt`은 서버 시각(ISO-8601). */
data class SealResponse(
    val recordId: String,
    val sha256: String,
    val sealedAt: String,
)
