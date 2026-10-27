package team.momentlog.app.core.error

/**
 * 성공(Right) 또는 실패(Left).
 * Repository는 서버 호출 결과를 `Either<Failure, T>`로 돌려준다.
 */
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()

    val isLeft: Boolean get() = this is Left
    val isRight: Boolean get() = this is Right

    inline fun <T> fold(left: (L) -> T, right: (R) -> T): T = when (this) {
        is Left -> left(value)
        is Right -> right(value)
    }
}
