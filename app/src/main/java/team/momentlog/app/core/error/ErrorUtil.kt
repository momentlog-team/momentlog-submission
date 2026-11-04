package team.momentlog.app.core.error

import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

/** 예외를 화면에 보여 줄 수 있는 [Failure]로 바꾼다. */
object ErrorUtil {

    fun errorConvert(error: Throwable): Failure = when (error) {
        is HttpException -> fromHttp(error)
        is IOException -> Failure(message = "서버에 연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요.")
        else -> Failure(message = error.message ?: Failure.DEFAULT_MESSAGE)
    }

    private fun fromHttp(error: HttpException): Failure {
        val body = error.response()?.errorBody()?.string()
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: return Failure(status = error.code(), message = Failure.DEFAULT_MESSAGE)
        return Failure(
            status = error.code(),
            errorCode = json.get("errorCode")?.asString ?: json.get("error")?.asString,
            message = json.get("message")?.asString ?: Failure.DEFAULT_MESSAGE,
            detail = json.get("detail")?.asString,
        )
    }
}
