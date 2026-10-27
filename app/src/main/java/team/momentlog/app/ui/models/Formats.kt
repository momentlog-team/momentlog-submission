package team.momentlog.app.ui.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formats {
    fun clock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))

    fun date(millis: Long): String = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(millis))

    fun shortDate(millis: Long): String = SimpleDateFormat("MM.dd (E)", Locale.KOREA).format(Date(millis))
}
