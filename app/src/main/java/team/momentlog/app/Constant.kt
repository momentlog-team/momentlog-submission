package team.momentlog.app

/** 앱 전역 상수. */
object Constant {
    const val LOG_TAG = "Momentlog"

    /** 속도 0이 이만큼 이어지면 정차로 본다. */
    const val STOP_CONFIRM_MILLIS = 5_000L

    /** 이 속도 이상이면 주행 중으로 본다. */
    const val RESUME_SPEED_KPH = 3f

    /** 보관하지 않은 기록을 지우기까지 남겨 두는 기간. */
    const val RETENTION_DAYS = 7

    const val NETWORK_TIMEOUT_SECONDS = 5L
}
