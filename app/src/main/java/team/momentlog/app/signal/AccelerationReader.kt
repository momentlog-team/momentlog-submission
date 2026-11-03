package team.momentlog.app.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import android.util.Log

/** main Handlerでrecorderへの呼び出しをtickと直列化する。 */
internal class AccelerationReader(context: Context, private val handler: Handler) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var gravity: FloatArray? = null
    private var gravityAt = 0L
    private var active = false
    private var epochOffsetMillis = 0L
    private var receive: ((Long, Float, Float) -> Unit)? = null

    fun start(onAcceleration: (Long, Float, Float) -> Unit) {
        stop()
        val linear = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val tilt = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (linear == null || tilt == null) {
            Log.w("Momentlog", "가속도 센서 연결 불가: 선가속도 또는 중력 센서 미지원")
            return
        }
        epochOffsetMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        receive = onAcceleration
        active = true
        try {
            val gravityRegistered = manager.registerListener(this, tilt, SensorManager.SENSOR_DELAY_GAME, handler)
            val linearRegistered = manager.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME, handler)
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
        active = false
        manager?.unregisterListener(this)
        receive = null
        gravity = null
        gravityAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            gravity = event.values.copyOf()
            gravityAt = event.timestamp
            return
        }
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        val tilt = gravity ?: return
        if (kotlin.math.abs(event.timestamp - gravityAt) > 1_000_000_000L) return
        val horizontal = VehicleAcceleration.transform(event.values, tilt) ?: return
        receive?.invoke(epochOffsetMillis + event.timestamp / 1_000_000L, horizontal.forward, horizontal.right)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
