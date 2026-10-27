package team.momentlog.app.core.data.model.record

/** 가지고 있는 기록의 해시가 봉인된 원본과 같은지 묻는다. */
data class VerifyRequest(
    val sha256: String,
)
