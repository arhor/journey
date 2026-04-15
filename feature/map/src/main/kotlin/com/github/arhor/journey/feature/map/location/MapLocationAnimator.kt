package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MapLocationAnimator internal constructor(
    private val config: MapLocationAnimatorConfig,
) {
    internal fun animate(targets: Flow<LocationStabilizationSnapshot>): Flow<MapLocationAnimationSnapshot> = channelFlow {
        val updates = Channel<LocationStabilizationSnapshot>(capacity = Channel.UNLIMITED)
        val upstreamCollector = launch {
            targets.collect { updates.send(it) }
            updates.close()
        }

        val visualLane = LaneAnimator(config = config.visual, frameIntervalMillis = config.frameIntervalMillis)
        val cameraLane = LaneAnimator(config = config.camera, frameIntervalMillis = config.frameIntervalMillis)

        var upstreamClosed = false

        while (true) {
            var shouldEmitFromUpdate = false
            while (true) {
                val update = updates.tryReceive()
                if (update.isSuccess) {
                    val snapshot = update.getOrNull() ?: continue
                    val visualChanged = visualLane.acceptTarget(snapshot.visualLocation)
                    val cameraChanged = cameraLane.acceptTarget(snapshot.cameraLocation)
                    shouldEmitFromUpdate = shouldEmitFromUpdate || visualChanged || cameraChanged
                    continue
                }

                if (update.isClosed) {
                    upstreamClosed = true
                }
                break
            }

            if (shouldEmitFromUpdate) {
                send(
                    MapLocationAnimationSnapshot(
                        visualLocation = visualLane.currentLocation,
                        cameraLocation = cameraLane.currentLocation,
                    ),
                )
            }

            val hasActiveAnimation = visualLane.hasActiveAnimation || cameraLane.hasActiveAnimation
            if (upstreamClosed && !hasActiveAnimation) {
                break
            }

            val next = if (hasActiveAnimation) {
                withTimeoutOrNull(config.frameIntervalMillis) {
                    updates.receiveCatching()
                }
            } else {
                updates.receiveCatching()
            }

            if (next != null) {
                if (next.isClosed) {
                    upstreamClosed = true
                    continue
                }

                val snapshot = next.getOrNull() ?: continue
                val visualChanged = visualLane.acceptTarget(snapshot.visualLocation)
                val cameraChanged = cameraLane.acceptTarget(snapshot.cameraLocation)
                if (visualChanged || cameraChanged) {
                    send(
                        MapLocationAnimationSnapshot(
                            visualLocation = visualLane.currentLocation,
                            cameraLocation = cameraLane.currentLocation,
                        ),
                    )
                }
                continue
            }

            val visualAdvanced = visualLane.advanceFrame()
            val cameraAdvanced = cameraLane.advanceFrame()
            if (visualAdvanced || cameraAdvanced) {
                send(
                    MapLocationAnimationSnapshot(
                        visualLocation = visualLane.currentLocation,
                        cameraLocation = cameraLane.currentLocation,
                    ),
                )
            }
        }

        upstreamCollector.cancel()
    }
}

internal data class MapLocationAnimationSnapshot(
    val visualLocation: UserLocationFix? = null,
    val cameraLocation: UserLocationFix? = null,
)

internal data class MapLocationAnimatorConfig(
    val visual: MapLocationAnimatorLaneConfig = MapLocationAnimatorLaneConfig(
        snapDistanceMeters = 100.0,
        referenceSpeedMetersPerSecond = 20.0,
        minDurationMillis = 120L,
        maxDurationMillis = 450L,
    ),
    val camera: MapLocationAnimatorLaneConfig = MapLocationAnimatorLaneConfig(
        snapDistanceMeters = 150.0,
        referenceSpeedMetersPerSecond = 28.0,
        minDurationMillis = 100L,
        maxDurationMillis = 320L,
    ),
    val frameIntervalMillis: Long = 32L,
) {
    init {
        require(frameIntervalMillis > 0L)
    }
}

internal data class MapLocationAnimatorLaneConfig(
    val snapDistanceMeters: Double,
    val referenceSpeedMetersPerSecond: Double,
    val minDurationMillis: Long,
    val maxDurationMillis: Long,
) {
    init {
        require(snapDistanceMeters >= 0.0)
        require(referenceSpeedMetersPerSecond > 0.0)
        require(minDurationMillis > 0L)
        require(maxDurationMillis >= minDurationMillis)
    }
}

private data class ActiveLaneAnimation(
    val startLocation: UserLocationFix,
    val targetLocation: UserLocationFix,
    val frameCount: Int,
    val frameIndex: Int,
)

private class LaneAnimator(
    private val config: MapLocationAnimatorLaneConfig,
    private val frameIntervalMillis: Long,
) {
    var currentLocation: UserLocationFix? = null
        private set

    private var activeAnimation: ActiveLaneAnimation? = null

    val hasActiveAnimation: Boolean
        get() = activeAnimation != null

    fun acceptTarget(target: UserLocationFix?): Boolean {
        if (target == null) {
            val changed = currentLocation != null || activeAnimation != null
            currentLocation = null
            activeAnimation = null
            return changed
        }

        val current = currentLocation
        if (current == null) {
            currentLocation = target
            activeAnimation = null
            return true
        }

        val distanceMeters = current.location.distanceTo(target.location)
        if (distanceMeters <= LOCATION_EPSILON_METERS || distanceMeters >= config.snapDistanceMeters) {
            val changed = current != target || activeAnimation != null
            currentLocation = target
            activeAnimation = null
            return changed
        }

        val frameCount = frameCountFor(distanceMeters)
        if (frameCount <= 1) {
            val changed = current != target || activeAnimation != null
            currentLocation = target
            activeAnimation = null
            return changed
        }

        activeAnimation = ActiveLaneAnimation(
            startLocation = current,
            targetLocation = target,
            frameCount = frameCount,
            frameIndex = 0,
        )
        return true
    }

    fun advanceFrame(): Boolean {
        val animation = activeAnimation ?: return false
        val nextFrameIndex = animation.frameIndex + 1

        if (nextFrameIndex >= animation.frameCount) {
            currentLocation = animation.targetLocation
            activeAnimation = null
            return true
        }

        val fraction = nextFrameIndex.toDouble() / animation.frameCount.toDouble()
        val start = animation.startLocation.location
        val target = animation.targetLocation.location

        currentLocation = animation.targetLocation.copy(
            location = GeoPoint(
                lat = start.lat + (target.lat - start.lat) * fraction,
                lon = start.lon + (target.lon - start.lon) * fraction,
            ),
            elapsedRealtimeNanos = animation.startLocation.elapsedRealtimeNanos.interpolateTo(
                target = animation.targetLocation.elapsedRealtimeNanos,
                fraction = fraction,
            ),
        )
        activeAnimation = animation.copy(frameIndex = nextFrameIndex)
        return true
    }

    private fun frameCountFor(distanceMeters: Double): Int {
        val rawDurationMillis = (distanceMeters / config.referenceSpeedMetersPerSecond * 1_000.0).roundToLong()
        val boundedDurationMillis = rawDurationMillis.coerceIn(
            minimumValue = config.minDurationMillis,
            maximumValue = config.maxDurationMillis,
        )
        return ceil(boundedDurationMillis.toDouble() / frameIntervalMillis.toDouble()).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val LOCATION_EPSILON_METERS: Double = 0.01
    }
}

private fun Long?.interpolateTo(
    target: Long?,
    fraction: Double,
): Long? {
    if (this == null || target == null) {
        return target
    }

    return (this + (target - this) * fraction).roundToLong()
}
