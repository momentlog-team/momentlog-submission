package team.momentlog.app.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import android.util.Log

/** 메인 Handler에서 recorder 호출과 tick을 직렬 처리한다. */
internal class AccelerationReader(context: Context, private val handler: Handler) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var gravity: FloatArray? = null
    private var gravityAt = 0L
    private var active = false
    private var gravityReceived = false
    private var linearReceived = false
    private var accelerationForwarded = false
    private var epochOffsetMillis = 0L
    private var receive: ((Long, Float, Float) -> Unit)? = null

    fun start(onAcceleration: (Long, Float, Float) -> Unit) {
        stop()
        val linear = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val tilt = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (linear == null || tilt == null) {
            Log.w("Momentlog", "가속도 센서 미지원: TYPE_LINEAR_ACCELERATION=${linear != null}, TYPE_GRAVITY=${tilt != null}")
            return
        }
        epochOffsetMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        receive = onAcceleration
        active = true
        try {
            val gravityRegistered = manager.registerListener(this, tilt, SensorManager.SENSOR_DELAY_GAME, handler)
            val linearRegistered = manager.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME, handler)
            Log.i("Momentlog", "가속도 센서 등록 결과: TYPE_LINEAR_ACCELERATION=$linearRegistered, TYPE_GRAVITY=$gravityRegistered")
            if (!gravityRegistered || !linearRegistered) {
                stop()
                Log.w("Momentlog", "가속도 센서 등록 실패")
            }
        } catch (e: RuntimeException) {
            stop()
            Log.w("Momentlog", "가속도 센서 등록 실패", e)
        }
    }

    fun stop() {
        if (active) Log.i("Momentlog", "가속도 센서 구독 해제")
        active = false
        manager?.unregisterListener(this)
        receive = null
        gravity = null
        gravityAt = 0L
        gravityReceived = false
        linearReceived = false
        accelerationForwarded = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            if (!gravityReceived) {
                gravityReceived = true
                Log.i("Momentlog", "TYPE_GRAVITY 첫 샘플 수신")
            }
            gravity = event.values.copyOf()
            gravityAt = event.timestamp
            return
        }
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        if (!linearReceived) {
            linearReceived = true
            Log.i("Momentlog", "TYPE_LINEAR_ACCELERATION 첫 샘플 수신")
        }
        val tilt = gravity ?: return
        if (kotlin.math.abs(event.timestamp - gravityAt) > 1_000_000_000L) return
        val horizontal = VehicleAcceleration.transform(event.values, tilt) ?: return
        if (!accelerationForwarded) {
            accelerationForwarded = true
            Log.i("Momentlog", "차량 수평 가속도 변환 성공: core 전달 시작")
        }
        receive?.invoke(epochOffsetMillis + event.timestamp / 1_000_000L, horizontal.forward, horizontal.right)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
