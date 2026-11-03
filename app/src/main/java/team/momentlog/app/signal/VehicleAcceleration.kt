package team.momentlog.app.signal

import kotlin.math.sqrt

/** 화면 법선(-Z)이 차량 앞쪽인 고정 장착용. 중력으로 pitch/roll을 보정한다.
 * 화면 회전과 무관한 센서 원좌표를 사용한다. yaw는 장착 방향 실측이 필요하다.
 */
internal object VehicleAcceleration {
    data class Horizontal(val forward: Float, val right: Float)

    fun transform(acceleration: FloatArray, gravity: FloatArray): Horizontal? {
        if (acceleration.size < 3 || gravity.size < 3 ||
            (0..2).any { !acceleration[it].isFinite() || !gravity[it].isFinite() }) return null
        val length = sqrt(gravity.take(3).sumOf { (it * it).toDouble() }).toFloat()
        if (length < 1f) return null
        val ux = gravity[0] / length
        val uy = gravity[1] / length
        val uz = gravity[2] / length
        // -Z를 수평면에 투영한다. 수평에 가까운 화면에서는 앞쪽을 결정할 수 없다.
        val horizontal = sqrt(ux * ux + uy * uy)
        if (horizontal < 0.2f) return null
        val fx = uz * ux / horizontal
        val fy = uz * uy / horizontal
        val fz = (-1f + uz * uz) / horizontal
        val rx = uy / horizontal
        val ry = -ux / horizontal
        return Horizontal(
            acceleration[0] * fx + acceleration[1] * fy + acceleration[2] * fz,
            acceleration[0] * rx + acceleration[1] * ry,
        )
    }
}
