package com.github.arhor.journey.data.healthconnect

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class HealthConnectAvailabilityChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HealthConnectAvailabilityRepository {

    override fun checkAvailability(): HealthConnectAvailability {
        val sdkStatus = HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER_PACKAGE_NAME)

        return mapHealthConnectAvailability(
            sdkStatus = sdkStatus,
            isProviderInstalled = isProviderInstalled(),
        )
    }

    private fun isProviderInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(
            HEALTH_CONNECT_PROVIDER_PACKAGE_NAME,
            PackageManager.PackageInfoFlags.of(0),
        )
    }.isSuccess

    private companion object {
        const val HEALTH_CONNECT_PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
    }
}

internal fun mapHealthConnectAvailability(
    sdkStatus: Int,
    isProviderInstalled: Boolean,
): HealthConnectAvailability = when (sdkStatus) {
    HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
        HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL
    }
    HealthConnectClient.SDK_UNAVAILABLE -> {
        if (isProviderInstalled) HealthConnectAvailability.NOT_SUPPORTED
        else HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL
    }
    else -> HealthConnectAvailability.NOT_SUPPORTED
}
