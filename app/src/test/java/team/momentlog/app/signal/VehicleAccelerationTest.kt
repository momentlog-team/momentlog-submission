package team.momentlog.app.signal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import team.momentlog.core.IncidentRecorder
import team.momentlog.core.Trigger

class VehicleAccelerationTest {
    @Test fun `기울어진 장착에서도 앞뒤 좌우 성분을 복원하고 수직 충격을 버린다`() {
        // X축을 중심으로 30도 기울인 화면: up=(0,cos,sin), forward=(0,sin,-cos).
        val c = 0.8660254f
        val gravity = floatArrayOf(0f, 9.81f * c, 4.905f)
        val acceleration = floatArrayOf(3f, 8f + 20f * c, -16f * c + 10f)
        val result = VehicleAcceleration.transform(acceleration, gravity)!!
        assertEquals(16f, result.forward, 0.001f)
        assertEquals(3f, result.right, 0.001f)
        val vertical = VehicleAcceleration.transform(gravity.map { it * 3 }.toFloatArray(), gravity)!!
        assertEquals(0f, vertical.forward, 0.001f)
        assertEquals(0f, vertical.right, 0.001f)
    }

    @Test fun `가로로 회전한 기기에서도 차량 축을 유지한다`() {
        val result = VehicleAcceleration.transform(floatArrayOf(0f, -4f, -16f), floatArrayOf(9.81f, 0f, 0f))!!
        assertEquals(16f, result.forward, 0.001f)
        assertEquals(4f, result.right, 0.001f)
    }

    @Test fun `방향을 결정할 수 없거나 유효하지 않은 값은 전달하지 않는다`() {
        assertNull(VehicleAcceleration.transform(floatArrayOf(1f, 2f, 3f), floatArrayOf(0f, 0f, 9.81f)))
        assertNull(VehicleAcceleration.transform(floatArrayOf(Float.NaN, 0f, 0f), floatArrayOf(0f, 9.81f, 0f)))
    }

    @Test fun `변환된 충격은 스냅샷을 기다리지 않고 기록을 시작하며 10초 내 중복은 core가 막는다`() {
        val recorder = IncidentRecorder()
        val horizontal = VehicleAcceleration.transform(floatArrayOf(0f, 0f, -16f), floatArrayOf(0f, 9.81f, 0f))!!
        recorder.onAcceleration(40_000, horizontal.forward, horizontal.right)
        assertTrue(recorder.isCapturing)
        assertEquals(Trigger.IMPACT, recorder.pendingTrigger)
        recorder.onAcceleration(40_020, horizontal.forward, horizontal.right)
        assertEquals(1, recorder.pendingEvents.size)
    }
}
