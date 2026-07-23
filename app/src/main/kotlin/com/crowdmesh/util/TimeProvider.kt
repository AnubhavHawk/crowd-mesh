package com.crowdmesh.util

import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper around wall-clock time so use-cases/tests can substitute a fake clock. */
interface TimeProvider {
    fun nowMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
