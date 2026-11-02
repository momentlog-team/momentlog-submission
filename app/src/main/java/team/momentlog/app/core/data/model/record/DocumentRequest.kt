package team.momentlog.app.core.data.model.record

/** 본문은 RecordHasher.canonical 결과를 그대로 전송한다. */
data class DocumentRequest(val body: String, val statementJson: String?, val placeText: String? = null)

data class DocumentResponse(
    val shareId: String,
    val recordId: String,
    val token: String,
    val path: String,
    val expiresAt: String,
)
