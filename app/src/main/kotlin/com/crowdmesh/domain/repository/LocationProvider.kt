package com.crowdmesh.domain.repository

import com.crowdmesh.domain.model.LocationFix

/** Abstraction over the platform location APIs so domain/use-case code stays framework-free. */
interface LocationProvider {
    /** Returns a single fresh fix, or null if one couldn't be obtained (permissions, no signal, timeout). */
    suspend fun getCurrentFix(): LocationFix?
}
