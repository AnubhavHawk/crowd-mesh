package com.crowdmesh.domain.repository

/** Abstraction over the on-device random UUID identity (see [com.crowdmesh.identity.DeviceIdentityProvider]). */
interface IdentityProvider {
    suspend fun getOrCreateDeviceId(): String
}
