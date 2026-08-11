package com.lumix.estimator.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?
)

/**
 * Wraps `FusedLocationProviderClient` with permission checks baked into every entry point, so
 * a caller can never crash from a missing-permission call. Per spec, GPS/location permission
 * being denied must never make the app unusable — every caller of this class has a manual
 * entry path that works with zero location access at all.
 */
class DeviceLocationManager(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** One-shot last-known location. Returns null (never throws) if permission is missing or nothing is cached yet. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): DeviceLocation? {
        if (!hasPermission()) return null
        return suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    val result = location?.let {
                        DeviceLocation(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitudeMeters = if (it.hasAltitude()) it.altitude else null,
                            accuracyMeters = if (it.hasAccuracy()) it.accuracy else null
                        )
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    /** Continuous location updates as a cold [Flow]; cancelling the collector stops updates. Empty if permission is missing. */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long = 5_000L): Flow<DeviceLocation> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        DeviceLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                        )
                    )
                }
            }
        }
        client.requestLocationUpdates(request, callback, null)
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
