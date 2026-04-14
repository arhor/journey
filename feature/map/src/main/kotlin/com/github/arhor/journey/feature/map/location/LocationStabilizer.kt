package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import javax.inject.Inject

class LocationStabilizer @Inject constructor(
    private val config: LocationStabilizerConfig,
) {
    private var lastInput: UserLocationFix? = null
    private var visualState: LocationPolicyState? = null
    private var cameraState: LocationPolicyState? = null

    fun stabilize(input: UserLocationFix?): LocationStabilizationSnapshot {
        if (input == null) {
            return snapshot()
        }

        if (input == lastInput) {
            return snapshot()
        }

        lastInput = input
        visualState = config.visualPolicy.stabilize(previous = visualState, input = input)
        cameraState = config.cameraPolicy.stabilize(previous = cameraState, input = input)

        return snapshot()
    }

    fun reset() {
        lastInput = null
        visualState = null
        cameraState = null
    }

    private fun snapshot(): LocationStabilizationSnapshot =
        LocationStabilizationSnapshot(
            visualLocation = visualState?.displayedLocation,
            cameraLocation = cameraState?.displayedLocation,
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
        stationarySpeedMetersPerSecond = 1.5,
        stationaryRadiusMeters = 10.0,
        releaseRadiusMeters = 10.0,
        releaseConfirmationCount = 2,
        accuracyRadiusFactor = 0.5,
        maxStationaryRadiusMeters = 35.0,
    ),
    val cameraPolicy: LocationStabilizationPolicy = LocationStabilizationPolicy(
        maxHorizontalAccuracyMeters = 50.0,
        minMovementMeters = 5.0,
        smoothingAlpha = 0.65,
        minBearingSpeedMetersPerSecond = 1.5,
        snapDistanceMeters = 150.0,
        stationarySpeedMetersPerSecond = 1.5,
        stationaryRadiusMeters = 15.0,
        releaseRadiusMeters = 15.0,
        releaseConfirmationCount = 2,
        accuracyRadiusFactor = 0.5,
        maxStationaryRadiusMeters = 35.0,
    ),
)

data class LocationStabilizationPolicy(
    val maxHorizontalAccuracyMeters: Double,
    val minMovementMeters: Double,
    val smoothingAlpha: Double,
    val minBearingSpeedMetersPerSecond: Double,
    val snapDistanceMeters: Double,
    val stationarySpeedMetersPerSecond: Double,
    val stationaryRadiusMeters: Double,
    val releaseRadiusMeters: Double,
    val releaseConfirmationCount: Int,
    val accuracyRadiusFactor: Double,
    val maxStationaryRadiusMeters: Double,
) {
    init {
        require(maxHorizontalAccuracyMeters > 0.0)
        require(minMovementMeters >= 0.0)
        require(smoothingAlpha in 0.0..1.0)
        require(minBearingSpeedMetersPerSecond >= 0.0)
        require(snapDistanceMeters >= minMovementMeters)
        require(stationarySpeedMetersPerSecond >= 0.0)
        require(stationaryRadiusMeters >= minMovementMeters)
        require(releaseRadiusMeters >= stationaryRadiusMeters)
        require(releaseConfirmationCount > 0)
        require(accuracyRadiusFactor >= 0.0)
        require(maxStationaryRadiusMeters >= stationaryRadiusMeters)
    }

    internal fun stabilize(
        previous: LocationPolicyState?,
        input: UserLocationFix,
    ): LocationPolicyState? {
        if (!input.isAccurateEnough()) {
            return previous
        }

        val sanitized = input.withStableBearing()
        if (previous == null) {
            return LocationPolicyState(displayedLocation = sanitized)
        }

        val displayed = previous.displayedLocation
        val movementMeters = displayed.location.distanceTo(sanitized.location)
        val resolvedStationaryRadiusMeters = sanitized.resolvedStationaryRadiusMeters()
        val resolvedReleaseRadiusMeters = maxOf(releaseRadiusMeters, resolvedStationaryRadiusMeters)

        return when {
            movementMeters <= minMovementMeters -> previous.copy(
                displayedLocation = displayed.withStationaryMetadataFrom(sanitized),
                pendingMovementCount = 0,
            )

            movementMeters >= snapDistanceMeters -> LocationPolicyState(
                displayedLocation = sanitized,
            )

            sanitized.hasMovingSpeed() && movementMeters > minMovementMeters -> LocationPolicyState(
                displayedLocation = displayed.smoothToward(sanitized),
            )

            movementMeters <= resolvedStationaryRadiusMeters -> previous.copy(
                displayedLocation = displayed.withStationaryMetadataFrom(sanitized),
                pendingMovementCount = 0,
            )

            movementMeters >= resolvedReleaseRadiusMeters -> {
                val pendingMovementCount = previous.pendingMovementCount + 1
                if (pendingMovementCount >= releaseConfirmationCount) {
                    LocationPolicyState(
                        displayedLocation = displayed.smoothToward(sanitized),
                    )
                } else {
                    previous.copy(
                        displayedLocation = displayed.withStationaryMetadataFrom(sanitized),
                        pendingMovementCount = pendingMovementCount,
                    )
                }
            }

            else -> previous.copy(
                displayedLocation = displayed.withStationaryMetadataFrom(sanitized),
                pendingMovementCount = 0,
            )
        }
    }

    private fun UserLocationFix.isAccurateEnough(): Boolean =
        horizontalAccuracyMeters
            ?.let { it <= maxHorizontalAccuracyMeters }
            ?: false

    private fun UserLocationFix.withStableBearing(): UserLocationFix {
        val speed = speedMetersPerSecond ?: 0.0
        val stableBearing = bearingDegrees?.takeIf { speed >= minBearingSpeedMetersPerSecond }
        return copy(
            bearingDegrees = stableBearing,
            bearingAccuracyDegrees = bearingAccuracyDegrees.takeIf { stableBearing != null },
        )
    }

    private fun UserLocationFix.hasMovingSpeed(): Boolean =
        (speedMetersPerSecond ?: 0.0) >= stationarySpeedMetersPerSecond

    private fun UserLocationFix.resolvedStationaryRadiusMeters(): Double {
        val accuracyRadiusMeters = horizontalAccuracyMeters
            ?.times(accuracyRadiusFactor)
            ?: 0.0

        return maxOf(
            stationaryRadiusMeters,
            accuracyRadiusMeters,
        ).coerceAtMost(maxStationaryRadiusMeters)
    }

    private fun UserLocationFix.withStationaryMetadataFrom(input: UserLocationFix): UserLocationFix =
        copy(
            horizontalAccuracyMeters = input.horizontalAccuracyMeters,
            speedMetersPerSecond = input.speedMetersPerSecond,
            bearingDegrees = input.bearingDegrees,
            bearingAccuracyDegrees = input.bearingAccuracyDegrees,
            elapsedRealtimeNanos = input.elapsedRealtimeNanos,
        )

    private fun UserLocationFix.smoothToward(target: UserLocationFix): UserLocationFix =
        target.copy(
            location = location.smoothToward(
                target = target.location,
                alpha = smoothingAlpha,
            ),
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

internal data class LocationPolicyState(
    val displayedLocation: UserLocationFix,
    val pendingMovementCount: Int = 0,
)
