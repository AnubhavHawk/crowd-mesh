package com.crowdmesh.fakes

import com.crowdmesh.domain.repository.IdentityProvider

class FakeIdentityProvider(private val deviceId: String = "own-device") : IdentityProvider {
    override suspend fun getOrCreateDeviceId(): String = deviceId
}
