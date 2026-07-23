package com.crowdmesh.util

import android.util.Log
import com.crowdmesh.BuildConfig

/**
 * Thin logging seam. CrowdMesh sends nothing anywhere (no analytics, no
 * crash reporting, no network) — this exists purely to gate noisy mesh/BLE
 * logs out of release builds, not to route data off-device.
 */
object Logger {
    private const val TAG_PREFIX = "CrowdMesh:"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG_PREFIX + tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG_PREFIX + tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG_PREFIX + tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG_PREFIX + tag, message, throwable)
    }
}
