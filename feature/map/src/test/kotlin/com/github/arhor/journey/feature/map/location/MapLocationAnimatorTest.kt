package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MapLocationAnimatorTest {

    @Test
    fun `animate should emit intermediate positions between accepted fixes`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0, bearing = 10.0)
        val target = fix(lat = 0.0, lon = 0.0001, bearing = 95.0)

        // When
        runCurrent()
        input.emit(
            LocationStabilizationSnapshot(
                visualLocation = initial,
                cameraLocation = initial,
            ),
        )
        runCurrent()
        input.emit(
            LocationStabilizationSnapshot(
                visualLocation = target,
                cameraLocation = target,
            ),
        )
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val intermediate = emissions.last().visualLocation
        advanceTimeBy(1_000L)
        runCurrent()
        val final = emissions.last().visualLocation

        // Then
        intermediate?.location?.lon?.let { it > 0.0 && it < target.location.lon } shouldBe true
        intermediate?.bearingDegrees shouldBe target.bearingDegrees
        final?.location?.lon shouldBe (target.location.lon plusOrMinus 0.0000001)

        collector.cancel()
    }

    @Test
    fun `animate should snap on large jump instead of slow trailing animation`() = runTest {
        // Given
        val subject = MapLocationAnimator(
            config = testConfig(
                visual = laneConfig(snapDistanceMeters = 50.0),
                camera = laneConfig(snapDistanceMeters = 50.0),
            ),
        )
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0)
        val farTarget = fix(lat = 0.0, lon = 0.01)

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(farTarget, farTarget))
        runCurrent()
        val sizeAfterSnap = emissions.size
        advanceTimeBy(500L)
        runCurrent()

        // Then
        emissions.last().visualLocation?.location shouldBe farTarget.location
        emissions.last().cameraLocation?.location shouldBe farTarget.location
        emissions.size shouldBe sizeAfterSnap

        collector.cancel()
    }

    @Test
    fun `animate should support different visual and camera targets`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0)
        val visualTarget = fix(lat = 0.0, lon = 0.00012)
        val cameraTarget = fix(lat = 0.00012, lon = 0.0)

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(visualTarget, cameraTarget))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val animated = emissions.last()
        advanceTimeBy(1_000L)
        runCurrent()
        val final = emissions.last()

        // Then
        animated.visualLocation?.location?.lon?.let { it > 0.0 && it < visualTarget.location.lon } shouldBe true
        animated.cameraLocation?.location?.lat?.let { it > 0.0 && it < cameraTarget.location.lat } shouldBe true
        animated.visualLocation?.location?.lat shouldBe (0.0 plusOrMinus 0.0000001)
        animated.cameraLocation?.location?.lon shouldBe (0.0 plusOrMinus 0.0000001)
        final.visualLocation?.location shouldBe visualTarget.location
        final.cameraLocation?.location shouldBe cameraTarget.location

        collector.cancel()
    }

    @Test
    fun `animate should retarget from latest displayed frame while animation is active`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0)
        val firstTarget = fix(lat = 0.0, lon = 0.00012)
        val secondTarget = fix(lat = 0.0, lon = 0.00018)

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(firstTarget, firstTarget))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val retargetStartLon = emissions.last().visualLocation?.location?.lon
        input.emit(LocationStabilizationSnapshot(secondTarget, secondTarget))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val retargetedFrameLon = emissions.last().visualLocation?.location?.lon
        advanceTimeBy(1_000L)
        runCurrent()
        val final = emissions.last().visualLocation

        // Then
        retargetStartLon shouldBe ((firstTarget.location.lon / 6.0) plusOrMinus 0.0000001)
        retargetedFrameLon?.let { it > retargetStartLon!! && it < secondTarget.location.lon } shouldBe true
        final?.location shouldBe secondTarget.location

        collector.cancel()
    }

    @Test
    fun `animate should reset immediately while animation is active`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0)
        val target = fix(lat = 0.0, lon = 0.00012)

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(target, target))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        input.emit(LocationStabilizationSnapshot(visualLocation = null, cameraLocation = null))
        runCurrent()
        val reset = emissions.last()
        advanceTimeBy(500L)
        runCurrent()

        // Then
        reset shouldBe MapLocationAnimationSnapshot()
        emissions.last() shouldBe MapLocationAnimationSnapshot()

        collector.cancel()
    }

    @Test
    fun `animate should reset locations immediately when target becomes unavailable`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0)
        val recovered = fix(lat = 0.0, lon = 0.0002)

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(visualLocation = null, cameraLocation = null))
        runCurrent()
        val sizeAfterReset = emissions.size
        advanceTimeBy(500L)
        runCurrent()
        input.emit(LocationStabilizationSnapshot(recovered, recovered))
        runCurrent()

        // Then
        emissions[sizeAfterReset - 1] shouldBe MapLocationAnimationSnapshot()
        emissions.size shouldBe (sizeAfterReset + 1)
        emissions.last().visualLocation?.location shouldBe recovered.location
        emissions.last().cameraLocation?.location shouldBe recovered.location

        collector.cancel()
    }

    @Test
    fun `animate should progress deterministically with frame interval timing`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0, elapsedRealtimeNanos = 1_000L)
        val target = fix(lat = 0.0, lon = 0.0001, elapsedRealtimeNanos = 7_000L)

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(target, target))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val firstFrameLon = emissions.last().visualLocation?.location?.lon
        val firstFrameElapsedRealtimeNanos = emissions.last().visualLocation?.elapsedRealtimeNanos
        advanceTimeBy(100L)
        runCurrent()
        val secondFrameLon = emissions.last().visualLocation?.location?.lon
        val secondFrameElapsedRealtimeNanos = emissions.last().visualLocation?.elapsedRealtimeNanos

        // Then
        firstFrameLon shouldBe ((target.location.lon / 6.0) plusOrMinus 0.0000001)
        firstFrameElapsedRealtimeNanos shouldBe 2_000L
        secondFrameLon shouldBe (((target.location.lon * 2.0) / 6.0) plusOrMinus 0.0000001)
        secondFrameElapsedRealtimeNanos shouldBe 3_000L

        collector.cancel()
    }

    @Test
    fun `animate should not carry previous bearing into unavailable target bearing frames`() = runTest {
        // Given
        val subject = MapLocationAnimator(config = testConfig())
        val input = MutableSharedFlow<LocationStabilizationSnapshot>(extraBufferCapacity = 8)
        val emissions = mutableListOf<MapLocationAnimationSnapshot>()
        val collector = backgroundScope.launch { subject.animate(input).collect { emissions += it } }
        val initial = fix(lat = 0.0, lon = 0.0, bearing = 12.0, bearingAccuracy = 9.0)
        val target = fix(
            lat = 0.0,
            lon = 0.00012,
            bearing = null,
            bearingAccuracy = null,
            speed = 0.3,
        )

        // When
        runCurrent()
        input.emit(LocationStabilizationSnapshot(initial, initial))
        runCurrent()
        input.emit(LocationStabilizationSnapshot(target, target))
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        val intermediate = emissions.last().visualLocation

        // Then
        intermediate?.location?.lat shouldBe (0.0 plusOrMinus 0.0000001)
        intermediate?.location?.lon shouldBe ((target.location.lon / 6.0) plusOrMinus 0.0000001)
        intermediate?.bearingDegrees shouldBe null
        intermediate?.bearingAccuracyDegrees shouldBe null
        intermediate?.speedMetersPerSecond shouldBe target.speedMetersPerSecond

        collector.cancel()
    }

    private fun testConfig(
        visual: MapLocationAnimatorLaneConfig = laneConfig(),
        camera: MapLocationAnimatorLaneConfig = laneConfig(),
    ): MapLocationAnimatorConfig =
        MapLocationAnimatorConfig(
            visual = visual,
            camera = camera,
            frameIntervalMillis = 100L,
        )

    private fun laneConfig(
        snapDistanceMeters: Double = 200.0,
    ): MapLocationAnimatorLaneConfig =
        MapLocationAnimatorLaneConfig(
            snapDistanceMeters = snapDistanceMeters,
            referenceSpeedMetersPerSecond = 10.0,
            minDurationMillis = 300L,
            maxDurationMillis = 600L,
        )

    private fun fix(
        lat: Double,
        lon: Double,
        bearing: Double? = null,
        bearingAccuracy: Double? = null,
        speed: Double? = 2.0,
        elapsedRealtimeNanos: Long = 1_000L,
    ): UserLocationFix =
        UserLocationFix(
            location = GeoPoint(lat = lat, lon = lon),
            horizontalAccuracyMeters = 10.0,
            speedMetersPerSecond = speed,
            bearingDegrees = bearing,
            bearingAccuracyDegrees = bearingAccuracy,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )
}
