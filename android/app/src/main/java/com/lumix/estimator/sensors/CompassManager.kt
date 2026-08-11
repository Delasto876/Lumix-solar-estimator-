package com.lumix.estimator.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [accuracy] is one of `SensorManager.SENSOR_STATUS_*`. */
data class CompassState(
    val magneticHeadingDegrees: Double,
    val trueHeadingDegrees: Double,
    val accuracy: Int
)

/**
 * Live device heading from `TYPE_ROTATION_VECTOR`, corrected from magnetic to true north via
 * [GeomagneticField] (which needs an approximate location — call [updateLocation] whenever the
 * site's location is known; heading is magnetic-only until then). Raw sensor readings are
 * smoothed with an exponential moving average rather than shown as-is, since a phone's raw
 * rotation vector is visibly jittery frame to frame.
 *
 * Solar orientation must be measured against true north, not magnetic north — the two differ
 * by several degrees depending on location, enough to matter for panel-facing accuracy.
 */
class CompassManager(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** True once a real device has a rotation-vector sensor; false on emulators/devices without one. */
    val isAvailable: Boolean get() = rotationSensor != null

    private var declinationDegrees = 0.0
    private var smoothedHeading: Double? = null

    private val _state = MutableStateFlow(CompassState(0.0, 0.0, SensorManager.SENSOR_STATUS_UNRELIABLE))
    val state: StateFlow<CompassState> = _state

    /** Declination depends on where on Earth you are — update it whenever the working location changes. */
    fun updateLocation(latitude: Double, longitude: Double, altitudeMeters: Double = 0.0) {
        declinationDegrees = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitudeMeters.toFloat(),
            System.currentTimeMillis()
        ).declination.toDouble()
    }

    fun start() {
        val sensor = rotationSensor ?: return
        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
        smoothedHeading = null
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val rawHeading = CompassMath.normalizeDegrees(Math.toDegrees(orientation[0].toDouble()))
            val smoothed = CompassMath.smooth(smoothedHeading, rawHeading)
            smoothedHeading = smoothed
            val trueHeading = CompassMath.normalizeDegrees(smoothed + declinationDegrees)

            _state.value = _state.value.copy(magneticHeadingDegrees = smoothed, trueHeadingDegrees = trueHeading)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            _state.value = _state.value.copy(accuracy = accuracy)
        }
    }
}
