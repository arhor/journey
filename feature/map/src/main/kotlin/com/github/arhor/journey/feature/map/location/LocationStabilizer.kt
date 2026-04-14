package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import javax.inject.Inject

class LocationStabilizer @Inject constructor(
    private val config: LocationStabilizerConfig,
) {
    private var lastInput: UserLocationFix? = null
    private var visualLocation: UserLocationFix? = null
    private var cameraLocation: UserLocationFix? = null

    fun stabilize(input: UserLocationFix?): LocationStabilizationSnapshot {
        if (input == null) {
            return snapshot()
        }

        if (input == lastInput) {
            return snapshot()
        }

        lastInput = input
        visualLocation = config.visualPolicy.stabilize(previous = visualLocation, input = input)
        cameraLocation = config.cameraPolicy.stabilize(previous = cameraLocation, input = input)

        return snapshot()
    }

    fun reset() {
        lastInput = null
        visualLocation = null
        cameraLocation = null
    }

    private fun snapshot(): LocationStabilizationSnapshot =
        LocationStabilizationSnapshot(
            visualLocation = visualLocation,
            cameraLocation = cameraLocation,
        )
}

data class LocationStabilizationSnapshot(
    val visualLocation: UserLocationFix?,
    val cameraLocation: UserLocationFix?,
)

data class LocationStabilizerConfig(
    val visualPolicy: LocationStabilizationPolicy = LocationStabilizationPolicy(
        maxHorizontalAccuracyMeters = 100.0,
        minMovementMeters = 2.0,
        smoothingAlpha = 0.75,
        minBearingSpeedMetersPerSecond = 1.0,
        snapDistanceMeters = 100.0,
    ),
    val cameraPolicy: LocationStabilizationPolicy = LocationStabilizationPolicy(
        maxHorizontalAccuracyMeters = 50.0,
        minMovementMeters = 5.0,
        smoothingAlpha = 0.65,
        minBearingSpeedMetersPerSecond = 1.5,
        snapDistanceMeters = 150.0,
    ),
)

data class LocationStabilizationPolicy(
    val maxHorizontalAccuracyMeters: Double,
    val minMovementMeters: Double,
    val smoothingAlpha: Double,
    val minBearingSpeedMetersPerSecond: Double,
    val snapDistanceMeters: Double,
) {
    init {
        require(maxHorizontalAccuracyMeters > 0.0)
        require(minMovementMeters >= 0.0)
        require(smoothingAlpha in 0.0..1.0)
        require(minBearingSpeedMetersPerSecond >= 0.0)
        require(snapDistanceMeters >= minMovementMeters)
    }

    fun stabilize(
        previous: UserLocationFix?,
        input: UserLocationFix,
    ): UserLocationFix? {
        if (!input.isAccurateEnough()) {
            return previous
        }

        val sanitized = input.withStableBearing()
        if (previous == null) {
            return sanitized
        }

        val movementMeters = previous.location.distanceTo(sanitized.location)
        return when {
            movementMeters <= minMovementMeters -> previous.withMetadataFrom(sanitized)
            movementMeters >= snapDistanceMeters -> sanitized
            else -> sanitized.copy(
                location = previous.location.smoothToward(
                    target = sanitized.location,
                    alpha = smoothingAlpha,
                ),
            )
        }
    }

    private fun UserLocationFix.isAccurateEnough(): Boolean =
        horizontalAccuracyMeters
            ?.let { it <= maxHorizontalAccuracyMeters }
            ?: true

    private fun UserLocationFix.withStableBearing(): UserLocationFix {
        val speed = speedMetersPerSecond ?: 0.0
        val stableBearing = bearingDegrees?.takeIf { speed >= minBearingSpeedMetersPerSecond }
        return copy(
            bearingDegrees = stableBearing,
            bearingAccuracyDegrees = bearingAccuracyDegrees.takeIf { stableBearing != null },
        )
    }

    private fun UserLocationFix.withMetadataFrom(input: UserLocationFix): UserLocationFix =
        copy(
            horizontalAccuracyMeters = input.horizontalAccuracyMeters,
            speedMetersPerSecond = input.speedMetersPerSecond,
            bearingDegrees = input.bearingDegrees,
            bearingAccuracyDegrees = input.bearingAccuracyDegrees,
            elapsedRealtimeNanos = input.elapsedRealtimeNanos,
        )

    private fun GeoPoint.smoothToward(
        target: GeoPoint,
        alpha: Double,
    ): GeoPoint =
        GeoPoint(
            lat = lat + (target.lat - lat) * alpha,
            lon = lon + (target.lon - lon) * alpha,
        )
}
