package com.github.arhor.journey.data.healthconnect

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Lazy
import javax.inject.Inject

class HealthConnectPermissionGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val healthConnectClient: Lazy<HealthConnectClient>,
) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    suspend fun getGrantedPermissions(): Set<String> =
        requireDeclaredPermissions().let {
            healthConnectClient.get().permissionController.getGrantedPermissions()
        }

    suspend fun getMissingPermissions(): Set<String> =
        requiredPermissions - getGrantedPermissions()

    suspend fun hasAllRequiredPermissions(): Boolean = getMissingPermissions().isEmpty()

    fun getUndeclaredPermissions(): Set<String> =
        requiredPermissions - getDeclaredPermissions()

    private fun requireDeclaredPermissions(): Set<String> {
        val undeclaredPermissions = getUndeclaredPermissions()
        check(undeclaredPermissions.isEmpty()) {
            "The installed app manifest is missing Health Connect permissions: ${undeclaredPermissions.joinToString()}."
        }

        return undeclaredPermissions
    }

    private fun getDeclaredPermissions(): Set<String> {
        return context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions?.toSet().orEmpty()
    }
}
