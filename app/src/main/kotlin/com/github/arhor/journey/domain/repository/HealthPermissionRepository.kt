package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.HealthDataType

interface HealthPermissionRepository {

    suspend fun hasReadPermissions(selectedDataTypes: Set<HealthDataType>): Boolean
}
