package com.github.arhor.journey.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HealthConnectPermissionGatewayTest {

    @Test
    fun `required permissions should include steps and exercise session read permissions`() {
        // Given
        val permissionController = mockk<PermissionController>()
        val healthConnectClient = mockk<HealthConnectClient>()
        every { healthConnectClient.permissionController } returns permissionController

        // When
        val gateway = HealthConnectPermissionGateway(healthConnectClient)

        // Then
        gateway.requiredPermissions.size shouldBe 2
        gateway.requiredPermissions shouldContainAll setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_EXERCISE",
        )
    }

    @Test
    fun `get missing permissions should return only permissions that are not granted`() = runTest {
        // Given
        val permissionController = mockk<PermissionController>()
        val healthConnectClient = mockk<HealthConnectClient>()
        every { healthConnectClient.permissionController } returns permissionController
        coEvery { permissionController.getGrantedPermissions() } returns setOf("android.permission.health.READ_STEPS")

        val gateway = HealthConnectPermissionGateway(healthConnectClient)

        // When
        val missingPermissions = gateway.getMissingPermissions()

        // Then
        missingPermissions shouldBe setOf("android.permission.health.READ_EXERCISE")
    }
}
