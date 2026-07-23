package com.crowdmesh.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.crowdmesh.domain.model.LocationFix
import com.crowdmesh.domain.repository.LocationProvider
import com.crowdmesh.util.Logger
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * One-shot GPS fix via Play Services Fused Location. This is the single
 * network-adjacent-looking dependency in the app, but it never talks to any
 * server: `getCurrentLocation` resolves purely from on-device GNSS/sensors
 * and works fine in airplane mode as long as GPS itself is left on.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentFix(): LocationFix? {
        if (!hasLocationPermission()) {
            Logger.w(TAG, "Location permission not granted; cannot fetch a fix")
            return null
        }

        val cancellationSource = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_ACCEPTABLE_AGE_MILLIS)
            .build()

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellationSource.cancel() }

            client.getCurrentLocation(request, cancellationSource.token)
                .addOnSuccessListener { location ->
                    val fix = location?.let {
                        LocationFix(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracyMeters = it.accuracy,
                            timestampMillis = it.time,
                        )
                    }
                    if (continuation.isActive) continuation.resume(fix)
                }
                .addOnFailureListener { error ->
                    Logger.w(TAG, "Failed to obtain location fix", error)
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "FusedLocationProvider"
        const val MAX_ACCEPTABLE_AGE_MILLIS = 60_000L
    }
}
