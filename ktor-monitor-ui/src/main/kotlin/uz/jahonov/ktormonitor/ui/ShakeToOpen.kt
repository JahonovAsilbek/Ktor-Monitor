package uz.jahonov.ktormonitor.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import kotlin.math.sqrt

/** Opens the monitor when the device is shaken, while one of the app's screens is in front. */
internal class ShakeToOpen(private val application: Application) : SensorEventListener {
    private val sensors = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShake = 0L

    fun start() {
        accelerometer ?: return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity !is KtorMonitorActivity) sensors.registerListener(this@ShakeToOpen, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }

            override fun onActivityPaused(activity: Activity) = sensors.unregisterListener(this@ShakeToOpen)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val force = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (force > SHAKE_THRESHOLD && now - lastShake > SHAKE_COOLDOWN_MILLIS) {
            lastShake = now
            KtorMonitorUi.open(application)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_THRESHOLD = 2.7f
        const val SHAKE_COOLDOWN_MILLIS = 1_000L
    }
}
