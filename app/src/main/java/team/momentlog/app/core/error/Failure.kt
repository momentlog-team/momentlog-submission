package team.momentlog.app.core.error

/** 서버 호출 실패. 서버가 내려준 오류 본문 또는 통신 오류를 담는다. */
data class Failure(
    val status: Int? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val detail: String? = null,
) {
    override fun toString(): String = message ?: DEFAULT_MESSAGE

    companion object {
        const val DEFAULT_MESSAGE = "오류가 발생했습니다."
    }
}
