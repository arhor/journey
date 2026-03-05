package com.github.arhor.journey.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import javax.inject.Inject

class HealthConnectPermissionGateway @Inject constructor(
    private val healthConnectClient: HealthConnectClient,
) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    suspend fun getGrantedPermissions(): Set<String> =
        healthConnectClient.permissionController.getGrantedPermissions()

    suspend fun getMissingPermissions(): Set<String> =
        requiredPermissions - getGrantedPermissions()

    suspend fun hasAllRequiredPermissions(): Boolean = getMissingPermissions().isEmpty()
}
