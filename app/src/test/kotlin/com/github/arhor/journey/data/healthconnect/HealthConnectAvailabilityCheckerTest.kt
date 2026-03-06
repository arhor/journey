package com.github.arhor.journey.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import io.kotest.matchers.shouldBe
import org.junit.Test

class HealthConnectAvailabilityCheckerTest {

    @Test
    fun `checkAvailability should return available when sdk status is available`() {
        // Given
        val sdkStatus = HealthConnectClient.SDK_AVAILABLE

        // When
        val availability = mapHealthConnectAvailability(
            sdkStatus = sdkStatus,
            isProviderInstalled = true,
        )

        // Then
        availability shouldBe HealthConnectAvailability.AVAILABLE
    }

    @Test
    fun `checkAvailability should return needs update or install when provider update is required`() {
        // Given
        val sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

        // When
        val availability = mapHealthConnectAvailability(
            sdkStatus = sdkStatus,
            isProviderInstalled = true,
        )

        // Then
        availability shouldBe HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL
    }

    @Test
    fun `checkAvailability should return needs update or install when sdk is unavailable and provider is not installed`() {
        // Given
        val sdkStatus = HealthConnectClient.SDK_UNAVAILABLE

        // When
        val availability = mapHealthConnectAvailability(
            sdkStatus = sdkStatus,
            isProviderInstalled = false,
        )

        // Then
        availability shouldBe HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL
    }

    @Test
    fun `checkAvailability should return not supported when sdk is unavailable and provider is installed`() {
        // Given
        val sdkStatus = HealthConnectClient.SDK_UNAVAILABLE

        // When
        val availability = mapHealthConnectAvailability(
            sdkStatus = sdkStatus,
            isProviderInstalled = true,
        )

        // Then
        availability shouldBe HealthConnectAvailability.NOT_SUPPORTED
    }
}
