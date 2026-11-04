package team.momentlog.app.record

/** 토큰·원본 본문 없이 화면에 전달하는 서버 동기화 상태. */
data class SyncStatus(
    val pendingSeals: Int = 0,
    val pendingRevocations: Int = 0,
    val sealFailures: List<Int?> = emptyList(),
    val revocationFailures: List<Int?> = emptyList(),
    val offline: Boolean = false,
    val checking: Boolean = false,
    val localError: Boolean = false,
    val checked: Boolean = false,
) {
    val needsAttention: Boolean get() = sealFailures.isNotEmpty() || revocationFailures.isNotEmpty() || localError
    val pending: Int get() = pendingSeals + pendingRevocations
    val title: String get() = when {
        localError -> "전송 상태 확인 필요"
        needsAttention -> "서버 전송 확인 필요"
        checking -> "서버 전송 확인 중"
        !checked -> "전송 상태 확인 중"
        pending > 0 -> if (offline) "연결을 기다리고 있어요" else "서버 전송 재시도 중"
        else -> "서버 전송 완료"
    }

    fun failureMessage(statuses: List<Int?>): String = when {
        401 in statuses -> "기기 인증에 실패했어요."
        403 in statuses -> "기록을 만든 기기에서만 처리할 수 있어요."
        else -> "서버에서 요청을 받아들이지 않았어요."
    }
}
