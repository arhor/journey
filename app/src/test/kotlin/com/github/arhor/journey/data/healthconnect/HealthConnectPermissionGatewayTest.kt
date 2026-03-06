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
    fun `required permissions should include only exercise session read permission`() {
        // Given
        val context = mockContext(requestedPermissions = arrayOf("android.permission.health.READ_EXERCISE"))
        val permissionController = mockk<PermissionController>()
        val healthConnectClient = mockk<HealthConnectClient>()
        val lazyHealthConnectClient = mockk<Lazy<HealthConnectClient>>()
        every { healthConnectClient.permissionController } returns permissionController
        every { lazyHealthConnectClient.get() } returns healthConnectClient

        // When
        val gateway = HealthConnectPermissionGateway(context, lazyHealthConnectClient)

        // Then
        gateway.requiredPermissions shouldBe setOf("android.permission.health.READ_EXERCISE")
    }

    @Test
    fun `get missing permissions should return only permissions that are not granted`() = runTest {
        // Given
        val context = mockContext(requestedPermissions = arrayOf("android.permission.health.READ_EXERCISE"))
        val permissionController = mockk<PermissionController>()
        val healthConnectClient = mockk<HealthConnectClient>()
        val lazyHealthConnectClient = mockk<Lazy<HealthConnectClient>>()
        every { healthConnectClient.permissionController } returns permissionController
        every { lazyHealthConnectClient.get() } returns healthConnectClient
        coEvery { permissionController.getGrantedPermissions() } returns emptySet()

        val gateway = HealthConnectPermissionGateway(context, lazyHealthConnectClient)

        // When
        val missingPermissions = gateway.getMissingPermissions()

        // Then
        missingPermissions shouldBe setOf("android.permission.health.READ_EXERCISE")
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
        error?.message shouldBe "The installed app manifest is missing Health Connect permissions: android.permission.health.READ_EXERCISE."
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
}
