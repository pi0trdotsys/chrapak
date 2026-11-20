package app.chrapak.schedule

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** A one-off look at the phone's surroundings, taken only when a nap is about to be assumed. */
object NapSensors {
    /** Gravity along the screen axis above this means the phone lies flat, face up or face down. */
    private const val FLAT_GRAVITY = 8.0f

    suspend fun read(context: Context, timeoutMs: Long = 2_000): NapSignals {
        val manager = context.getSystemService(SensorManager::class.java)
        val proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val light = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val wanted = listOfNotNull(proximity, light, accelerometer)
        val first = ConcurrentHashMap<Int, FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                first.putIfAbsent(event.sensor.type, event.values.copyOf())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        wanted.forEach { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        try {
            var waited = 0L
            while (first.size < wanted.size && waited < timeoutMs) {
                delay(100)
                waited += 100
            }
        } finally {
            manager.unregisterListener(listener)
        }
        val charging = context.getSystemService(BatteryManager::class.java).isCharging
        return NapSignals(
            // Binary proximity sensors report 0 when near and their maximum range when far.
            covered = first[Sensor.TYPE_PROXIMITY]?.let { it[0] < (proximity?.maximumRange ?: 5f) },
            flat = first[Sensor.TYPE_ACCELEROMETER]?.let { abs(it[2]) >= FLAT_GRAVITY },
            lux = first[Sensor.TYPE_LIGHT]?.get(0),
            charging = charging,
        )
    }
}
