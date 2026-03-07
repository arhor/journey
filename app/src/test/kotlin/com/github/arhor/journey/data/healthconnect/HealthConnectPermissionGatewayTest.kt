package com.github.arhor.journey.data.healthconnect

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import dagger.Lazy
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HealthConnectPermissionGatewayTest {

    @Test
    fun `required permissions should include all declared health data read permissions when gateway is initialized`() {
        // Given
        val context = mockContext(requestedPermissions = expectedRequiredPermissions.toTypedArray())
        val permissionController = mockk<PermissionController>()
        val healthConnectClient = mockk<HealthConnectClient>()
        val lazyHealthConnectClient = mockk<Lazy<HealthConnectClient>>()
        every { healthConnectClient.permissionController } returns permissionController
        every { lazyHealthConnectClient.get() } returns healthConnectClient

        // When
        val gateway = HealthConnectPermissionGateway(context, lazyHealthConnectClient)

        // Then
        gateway.requiredPermissions shouldBe expectedRequiredPermissions
    }

    @Test
    fun `get missing permissions should return only permissions that are not granted when permissions are partially granted`() = runTest {
        // Given
        val context = mockContext(requestedPermissions = expectedRequiredPermissions.toTypedArray())
        val permissionController = mockk<PermissionController>()
        val healthConnectClient = mockk<HealthConnectClient>()
        val lazyHealthConnectClient = mockk<Lazy<HealthConnectClient>>()
        every { healthConnectClient.permissionController } returns permissionController
        every { lazyHealthConnectClient.get() } returns healthConnectClient
        coEvery { permissionController.getGrantedPermissions() } returns setOf(permissionReadExercise)

        val gateway = HealthConnectPermissionGateway(context, lazyHealthConnectClient)

        // When
        val missingPermissions = gateway.getMissingPermissions()

        // Then
        missingPermissions shouldBe expectedRequiredPermissions - permissionReadExercise
    }

    @Test
    fun `get missing permissions should fail when installed manifest does not declare required permissions`() = runTest {
        // Given
        val context = mockContext(requestedPermissions = emptyArray())
        val healthConnectClient = mockk<HealthConnectClient>()
        val lazyHealthConnectClient = mockk<Lazy<HealthConnectClient>>()
        every { lazyHealthConnectClient.get() } returns healthConnectClient
        val gateway = HealthConnectPermissionGateway(context, lazyHealthConnectClient)

        // When
        val error = runCatching { gateway.getMissingPermissions() }.exceptionOrNull()

        // Then
        (error is IllegalStateException) shouldBe true
        error?.message shouldBe "The installed app manifest is missing Health Connect permissions: " +
            "android.permission.health.READ_EXERCISE, " +
            "android.permission.health.READ_STEPS, " +
            "android.permission.health.READ_DISTANCE, " +
            "android.permission.health.READ_SLEEP."
    }

    private fun mockContext(requestedPermissions: Array<String>): Context {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val packageInfo = PackageInfo().apply {
            this.requestedPermissions = requestedPermissions
        }
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.github.arhor.journey"
        every {
            packageManager.getPackageInfo(
                "com.github.arhor.journey",
                PackageManager.GET_PERMISSIONS,
            )
        } returns packageInfo
        return context
    }

    private companion object {
        const val permissionReadExercise = "android.permission.health.READ_EXERCISE"
        const val permissionReadSteps = "android.permission.health.READ_STEPS"
        const val permissionReadDistance = "android.permission.health.READ_DISTANCE"
        const val permissionReadSleep = "android.permission.health.READ_SLEEP"

        val expectedRequiredPermissions = setOf(
            permissionReadExercise,
            permissionReadSteps,
            permissionReadDistance,
            permissionReadSleep,
        )
    }
}
