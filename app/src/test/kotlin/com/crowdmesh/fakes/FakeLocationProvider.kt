package com.crowdmesh.fakes

import com.crowdmesh.domain.model.LocationFix
import com.crowdmesh.domain.repository.LocationProvider

class FakeLocationProvider(var nextFix: LocationFix? = LocationFix(12.9716, 77.5946, 5f, 1_700_000_000_000L)) : LocationProvider {
    override suspend fun getCurrentFix(): LocationFix? = nextFix
}
