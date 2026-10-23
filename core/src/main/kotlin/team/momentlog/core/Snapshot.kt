package team.momentlog.core

/** 방향지시등 상태. */
enum class TurnSignal { OFF, LEFT, RIGHT }

/**
 * 한 시점의 내 차 상태.
 * 차종이 지원하지 않거나 아직 값을 못 받은 항목은 null로 둔다(문서에 "기록 없음"으로 표시).
 *
 * 조향각 부호는 AAOS 관례(왼쪽이 음수)를 따른다. Pleos 실측으로 부호를 확인하기 전까지는
 * [IncidentNarrator]가 방향을 단정하지 않도록 [steeringLeftIsNegative] 설정으로 뒤집을 수 있게 둔다.
 */
data class Snapshot(
    val atMillis: Long,
    val speedKph: Float? = null,
    val brake: Boolean? = null,
    val turn: TurnSignal? = null,
    val steeringDeg: Float? = null,
    val lowBeam: Boolean? = null,
    val highBeam: Boolean? = null,
    val wiperOn: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * 차량 신호는 리스너로 "값이 갱신될 때" 받는다(Pleos SDK register* 방식).
 * 그래서 마지막으로 받은 값을 들고 있다가 일정 주기로 [snapshot]을 찍어 시계열을 만든다.
 */
class SignalState {
    @Volatile var speedKph: Float? = null
    @Volatile var brake: Boolean? = null
    @Volatile var turn: TurnSignal? = null
    @Volatile var steeringDeg: Float? = null
    @Volatile var lowBeam: Boolean? = null
    @Volatile var highBeam: Boolean? = null
    @Volatile var wiperOn: Boolean? = null
    @Volatile var latitude: Double? = null
    @Volatile var longitude: Double? = null

    fun snapshot(atMillis: Long) = Snapshot(
        atMillis = atMillis,
        speedKph = speedKph,
        brake = brake,
        turn = turn,
        steeringDeg = steeringDeg,
        lowBeam = lowBeam,
        highBeam = highBeam,
        wiperOn = wiperOn,
        latitude = latitude,
        longitude = longitude,
    )
}
