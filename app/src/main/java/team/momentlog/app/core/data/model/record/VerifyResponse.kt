package team.momentlog.app.core.data.model.record

/** 원본 대조 결과. `status`는 서버 `VerifyResult.Status` 이름 그대로 온다. */
data class VerifyResponse(
    val status: String,
    val sealedAt: String?,
)
