package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.github.arhor.journey.domain.repository.HealthSyncCheckpointRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreHealthSyncCheckpointRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HealthSyncCheckpointRepository {

    override suspend fun getLastSuccessfulSyncAt(): Instant? {
        val epochMillis = dataStore.data.first()[lastSuccessfulSyncAtEpochMillis] ?: return null
        return Instant.ofEpochMilli(epochMillis)
    }

    override suspend fun setLastSuccessfulSyncAt(timestamp: Instant) {
        dataStore.edit { prefs ->
            prefs[lastSuccessfulSyncAtEpochMillis] = timestamp.toEpochMilli()
        }
    }

    private companion object {
        val lastSuccessfulSyncAtEpochMillis = longPreferencesKey("health_last_successful_sync_at_epoch_millis")
    }
}
