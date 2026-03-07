package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.healthconnect.HealthConnectPermissionGateway
import com.github.arhor.journey.domain.model.HealthDataType
import com.github.arhor.journey.domain.repository.HealthPermissionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectPermissionRepositoryImpl @Inject constructor(
    private val gateway: HealthConnectPermissionGateway,
) : HealthPermissionRepository {

    override suspend fun hasReadPermissions(selectedDataTypes: Set<HealthDataType>): Boolean {
        if (selectedDataTypes.isEmpty()) {
            return false
        }

        return getMissingPermissions().isEmpty()
    }

    override suspend fun getMissingPermissions(): Set<String> = gateway.getMissingPermissions()
}
