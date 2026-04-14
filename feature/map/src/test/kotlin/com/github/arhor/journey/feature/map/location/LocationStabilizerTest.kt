package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class LocationStabilizerTest {

    @Test
    fun `stabilize should reject poor accuracy updates for camera follow`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 20.0)
        val poorCameraFix = fix(lat = 0.0, lon = 0.001, accuracy = 80.0)

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(poorCameraFix)

        // Then
        actual.cameraLocation?.location shouldBe initial.location
        actual.visualLocation?.location shouldBe poorCameraFix.location
    }

    @Test
    fun `stabilize should keep tiny drift inside movement deadband`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 2.0)
        val drift = fix(lat = 0.0, lon = 0.00001, accuracy = 12.0, speed = 3.0)

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(drift)

        // Then
        actual.visualLocation?.location shouldBe initial.location
        actual.visualLocation?.horizontalAccuracyMeters shouldBe 12.0
        actual.cameraLocation?.location shouldBe initial.location
        actual.cameraLocation?.speedMetersPerSecond shouldBe 3.0
    }

    @Test
    fun `stabilize should keep stationary jitter anchored inside visual and camera lock radius`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 0.0)

        // When
        subject.stabilize(initial)
        val firstJitter = subject.stabilize(fix(lat = 0.0, lon = 0.00003, accuracy = 10.0, speed = 0.2))
        val secondJitter = subject.stabilize(fix(lat = 0.0, lon = 0.00009, accuracy = 10.0, speed = 0.3))
        val thirdJitter = subject.stabilize(fix(lat = 0.00008, lon = 0.00008, accuracy = 30.0, speed = 0.1))

        // Then
        firstJitter.visualLocation?.location shouldBe initial.location
        firstJitter.cameraLocation?.location shouldBe initial.location
        secondJitter.visualLocation?.location shouldBe initial.location
        secondJitter.cameraLocation?.location shouldBe initial.location
        thirdJitter.visualLocation?.location shouldBe initial.location
        thirdJitter.cameraLocation?.location shouldBe initial.location
    }

    @Test
    fun `stabilize should reject unknown accuracy fixes`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0)
        val unknownAccuracy = fix(lat = 0.0, lon = 0.01, accuracy = null)

        // When
        val missingInitial = subject.stabilize(unknownAccuracy)
        subject.stabilize(initial)
        val actual = subject.stabilize(unknownAccuracy)

        // Then
        missingInitial.visualLocation shouldBe null
        missingInitial.cameraLocation shouldBe null
        actual.visualLocation?.location shouldBe initial.location
        actual.cameraLocation?.location shouldBe initial.location
    }

    @Test
    fun `stabilize should pass through large real movement without smoothing lag`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0)
        val realMovement = fix(lat = 0.0, lon = 0.01, accuracy = 10.0)

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(realMovement)

        // Then
        actual.visualLocation?.location shouldBe realMovement.location
        actual.cameraLocation?.location shouldBe realMovement.location
    }

    @Test
    fun `stabilize should move visual and camera locations for normal walking movement`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 1.6)
        val walkingMovement = fix(lat = 0.00018, lon = 0.0, accuracy = 10.0, speed = 1.6)

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(walkingMovement)

        // Then
        actual.visualLocation?.location?.lat shouldBe (0.000135 plusOrMinus 0.0000001)
        actual.cameraLocation?.location?.lat shouldBe (0.000117 plusOrMinus 0.0000001)
    }

    @Test
    fun `stabilize should ignore bearing below speed threshold`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val slowFix = fix(
            lat = 0.0,
            lon = 0.0,
            accuracy = 10.0,
            speed = 0.5,
            bearing = 90.0,
            bearingAccuracy = 15.0,
        )

        // When
        val actual = subject.stabilize(slowFix)

        // Then
        actual.visualLocation?.bearingDegrees shouldBe null
        actual.visualLocation?.bearingAccuracyDegrees shouldBe null
        actual.cameraLocation?.bearingDegrees shouldBe null
        actual.cameraLocation?.bearingAccuracyDegrees shouldBe null
    }

    @Test
    fun `stabilize should not rotate bearing for low speed stationary jitter`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 0.0)
        val jitter = fix(
            lat = 0.0,
            lon = 0.00005,
            accuracy = 10.0,
            speed = 0.4,
            bearing = 180.0,
            bearingAccuracy = 10.0,
        )

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(jitter)

        // Then
        actual.visualLocation?.location shouldBe initial.location
        actual.visualLocation?.bearingDegrees shouldBe null
        actual.cameraLocation?.location shouldBe initial.location
        actual.cameraLocation?.bearingDegrees shouldBe null
    }

    @Test
    fun `stabilize should update stable bearing while keeping deadband position anchored`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 0.0)
        val bearingUpdate = fix(
            lat = 0.0,
            lon = 0.00001,
            accuracy = 10.0,
            speed = 2.0,
            bearing = 90.0,
            bearingAccuracy = 8.0,
        )

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(bearingUpdate)

        // Then
        actual.visualLocation?.location shouldBe initial.location
        actual.visualLocation?.bearingDegrees shouldBe 90.0
        actual.visualLocation?.bearingAccuracyDegrees shouldBe 8.0
        actual.cameraLocation?.location shouldBe initial.location
        actual.cameraLocation?.bearingDegrees shouldBe 90.0
        actual.cameraLocation?.bearingAccuracyDegrees shouldBe 8.0
    }

    @Test
    fun `stabilize should allow visual bearing when speed only satisfies visual threshold`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val slowCameraFix = fix(
            lat = 0.0,
            lon = 0.0,
            accuracy = 10.0,
            speed = 1.2,
            bearing = 90.0,
            bearingAccuracy = 15.0,
        )

        // When
        val actual = subject.stabilize(slowCameraFix)

        // Then
        actual.visualLocation?.bearingDegrees shouldBe 90.0
        actual.visualLocation?.bearingAccuracyDegrees shouldBe 15.0
        actual.cameraLocation?.bearingDegrees shouldBe null
        actual.cameraLocation?.bearingAccuracyDegrees shouldBe null
    }

    @Test
    fun `stabilize should smooth accepted moving camera fixes predictably across updates`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())

        // When
        subject.stabilize(fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 2.0))
        val second = subject.stabilize(fix(lat = 0.0, lon = 0.001, accuracy = 10.0, speed = 2.0))
        val third = subject.stabilize(fix(lat = 0.0, lon = 0.0015, accuracy = 10.0, speed = 2.0))

        // Then
        second.cameraLocation?.location?.lon shouldBe (0.00065 plusOrMinus 0.0000001)
        third.cameraLocation?.location?.lon shouldBe (0.0012025 plusOrMinus 0.0000001)
    }

    @Test
    fun `stabilize should keep stopped jitter anchored after previously moving`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 1.6)
        val moving = fix(lat = 0.0002, lon = 0.0, accuracy = 10.0, speed = 1.6)
        val stoppedJitter = fix(lat = 0.00033, lon = 0.0, accuracy = 10.0, speed = 0.2)

        // When
        subject.stabilize(initial)
        val moved = subject.stabilize(moving)
        val actual = subject.stabilize(stoppedJitter)

        // Then
        actual.visualLocation?.location shouldBe moved.visualLocation?.location
        actual.cameraLocation?.location shouldBe moved.cameraLocation?.location
    }

    @Test
    fun `stabilize should release stationary lock after repeated outside radius fixes`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0, speed = 0.3)

        // When
        subject.stabilize(initial)
        val pending = subject.stabilize(fix(lat = 0.00018, lon = 0.0, accuracy = 10.0, speed = 0.4))
        val released = subject.stabilize(fix(lat = 0.0002, lon = 0.0, accuracy = 10.0, speed = 0.4))

        // Then
        pending.visualLocation?.location shouldBe initial.location
        pending.cameraLocation?.location shouldBe initial.location
        released.visualLocation?.location?.lat shouldBe (0.00015 plusOrMinus 0.0000001)
        released.cameraLocation?.location?.lat shouldBe (0.00013 plusOrMinus 0.0000001)
    }

    @Test
    fun `stabilize should allow visual and camera policies to differ`() {
        // Given
        val subject = LocationStabilizer(LocationStabilizerConfig())
        val initial = fix(lat = 0.0, lon = 0.0, accuracy = 10.0)
        val mediumAccuracyFix = fix(lat = 0.0, lon = 0.001, accuracy = 60.0)

        // When
        subject.stabilize(initial)
        val actual = subject.stabilize(mediumAccuracyFix)

        // Then
        actual.visualLocation?.location shouldBe mediumAccuracyFix.location
        actual.cameraLocation?.location shouldBe initial.location
    }

    private fun fix(
        lat: Double,
        lon: Double,
        accuracy: Double?,
        speed: Double? = null,
        bearing: Double? = null,
        bearingAccuracy: Double? = null,
    ): UserLocationFix =
        UserLocationFix(
            location = GeoPoint(lat = lat, lon = lon),
            horizontalAccuracyMeters = accuracy,
            speedMetersPerSecond = speed,
            bearingDegrees = bearing,
            bearingAccuracyDegrees = bearingAccuracy,
            elapsedRealtimeNanos = 1_000L,
        )
}
