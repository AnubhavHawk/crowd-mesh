package com.crowdmesh.di

import com.crowdmesh.data.location.FusedLocationProvider
import com.crowdmesh.data.repository.GossipLedgerRepositoryImpl
import com.crowdmesh.data.repository.PeerRepositoryImpl
import com.crowdmesh.data.repository.PresenceRepositoryImpl
import com.crowdmesh.domain.repository.GossipLedgerRepository
import com.crowdmesh.domain.repository.IdentityProvider
import com.crowdmesh.domain.repository.LocationProvider
import com.crowdmesh.domain.repository.MeshController
import com.crowdmesh.domain.repository.PeerRepository
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.identity.DeviceIdentityProvider
import com.crowdmesh.mesh.MeshEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds every domain-layer port to its concrete implementation — the one place `domain` gets wired to reality. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(impl: PresenceRepositoryImpl): PresenceRepository

    @Binds
    @Singleton
    abstract fun bindPeerRepository(impl: PeerRepositoryImpl): PeerRepository

    @Binds
    @Singleton
    abstract fun bindGossipLedgerRepository(impl: GossipLedgerRepositoryImpl): GossipLedgerRepository

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindIdentityProvider(impl: DeviceIdentityProvider): IdentityProvider

    @Binds
    @Singleton
    abstract fun bindMeshController(impl: MeshEngine): MeshController
}
