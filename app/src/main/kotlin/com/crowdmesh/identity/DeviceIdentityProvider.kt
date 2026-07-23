package com.crowdmesh.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crowdmesh.domain.repository.IdentityProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one piece of identity CrowdMesh has: a random UUID generated on first
 * launch and never sent anywhere off-device. No account, no login, no
 * server ever sees it — it only ever travels peer-to-peer as the `userId`
 * on this device's own [com.crowdmesh.domain.model.PresenceRecord].
 */
@Singleton
class DeviceIdentityProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : IdentityProvider {

    private val mutex = Mutex()

    override suspend fun getOrCreateDeviceId(): String {
        val existing = dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing

        return mutex.withLock {
            val doubleChecked = dataStore.data.first()[DEVICE_ID_KEY]
            if (doubleChecked != null) return@withLock doubleChecked

            val newId = UUID.randomUUID().toString()
            dataStore.edit { it[DEVICE_ID_KEY] = newId }
            newId
        }
    }

    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }
}
