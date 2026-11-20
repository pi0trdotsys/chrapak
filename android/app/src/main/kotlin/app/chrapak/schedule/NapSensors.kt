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

    /** Spread of the acceleration (m/s²) above which the phone is held or shaking; a table gives well under half of it. */
    private const val HELD_STD = 0.08f

    /** How long to watch the accelerometer for tremors. */
    private const val SAMPLE_MS = 1_200L

    suspend fun read(context: Context, timeoutMs: Long = 2_500): NapSignals {
        val manager = context.getSystemService(SensorManager::class.java)
        val proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val light = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val first = ConcurrentHashMap<Int, FloatArray>()
        val samples = java.util.concurrent.CopyOnWriteArrayList<FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) samples += event.values.copyOf()
                else first.putIfAbsent(event.sensor.type, event.values.copyOf())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listOfNotNull(proximity, light).forEach { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // The accelerometer is read faster and for a while: stillness is a spread over time, not one value.
        accelerometer?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        try {
            var waited = 0L
            val others = listOfNotNull(proximity, light).size
            while (waited < timeoutMs && (first.size < others || (accelerometer != null && waited < SAMPLE_MS))) {
                delay(100)
                waited += 100
            }
        } finally {
            manager.unregisterListener(listener)
        }
        val charging = context.getSystemService(BatteryManager::class.java).isCharging
        val gravity = samples.takeIf { it.size >= 5 }?.let { list -> list.map { it[2] }.average().toFloat() }
        return NapSignals(
            // Binary proximity sensors report 0 when near and their maximum range when far.
            covered = first[Sensor.TYPE_PROXIMITY]?.let { it[0] < (proximity?.maximumRange ?: 5f) },
            held = samples.takeIf { it.size >= 5 }?.let { spread(it) > HELD_STD },
            flat = gravity?.let { abs(it) >= FLAT_GRAVITY },
            faceDown = gravity?.let { it <= -FLAT_GRAVITY },
            lux = first[Sensor.TYPE_LIGHT]?.get(0),
            charging = charging,
        )
    }

    /** Standard deviation of the acceleration magnitude. */
    internal fun spread(samples: List<FloatArray>): Float {
        val magnitudes = samples.map { kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
        val mean = magnitudes.average()
        return kotlin.math.sqrt(magnitudes.sumOf { (it - mean) * (it - mean) } / magnitudes.size).toFloat()
    }
}
