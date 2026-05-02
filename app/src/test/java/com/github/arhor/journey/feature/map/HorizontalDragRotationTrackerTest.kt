package com.github.arhor.journey.feature.map

import android.view.MotionEvent
import com.github.arhor.journey.feature.map.gesture.PlayerCenteredCameraGestureTracker
import com.github.arhor.journey.feature.map.gesture.PlayerCenteredCameraGestureUpdate
import com.github.arhor.journey.feature.map.gesture.normalizeBearing
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class HorizontalDragRotationTrackerTest {

    private fun tracker(): PlayerCenteredCameraGestureTracker =
        PlayerCenteredCameraGestureTracker(
            dragThresholdPx = 10f,
            bearingDegreesPerPixel = 0.12,
            tiltDegreesPerPixel = 0.10,
            minTilt = 0.0,
            maxTilt = 60.0,
        )

    @Test
    fun `onMotionEvent should update bearing from horizontal drag using gesture start bearing`() {
        // Given
        val tracker = tracker()

        // When
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 100f,
            y = 100f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )
        val belowThreshold = tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 108f,
            y = 101f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )
        val aboveThreshold = tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 120f,
            y = 102f,
            pointerCount = 1,
            currentBearing = 99.0,
            currentTilt = 50.0,
        )

        // Then
        belowThreshold shouldBe PlayerCenteredCameraGestureUpdate()
        aboveThreshold.didStartInteraction shouldBe true
        aboveThreshold.bearing shouldBe (12.4 plusOrMinus 0.001)
        aboveThreshold.tilt shouldBe (19.8 plusOrMinus 0.001)
    }

    @Test
    fun `onMotionEvent should update and clamp tilt from vertical drag using gesture start tilt`() {
        // Given
        val tracker = tracker()

        // When
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 100f,
            y = 100f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )
        val upwardDrag = tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 100f,
            y = -500f,
            pointerCount = 1,
            currentBearing = 99.0,
            currentTilt = 50.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_UP,
            x = 100f,
            y = -500f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 100f,
            y = 100f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )
        val downwardDrag = tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 100f,
            y = 500f,
            pointerCount = 1,
            currentBearing = 99.0,
            currentTilt = 50.0,
        )

        // Then
        upwardDrag.tilt shouldBe 60.0
        downwardDrag.tilt shouldBe 0.0
    }

    @Test
    fun `onMotionEvent should ignore multi touch and end interaction`() {
        // Given
        val tracker = tracker()

        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 50f,
            y = 50f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 70f,
            y = 50f,
            pointerCount = 1,
            currentBearing = 10.0,
            currentTilt = 20.0,
        )

        // When
        val actual = tracker.onMotionEvent(
            action = MotionEvent.ACTION_POINTER_DOWN,
            x = 70f,
            y = 50f,
            pointerCount = 2,
            currentBearing = 17.0,
            currentTilt = 30.0,
        )

        // Then
        actual.didEndInteraction shouldBe true
        actual.bearing shouldBe null
        actual.tilt shouldBe null
    }

    @Test
    fun `onMotionEvent should end interaction on finger lift and cancellation`() {
        // Given
        val tracker = tracker()

        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
            currentTilt = 0.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
            currentTilt = 0.0,
        )

        // When
        val onUp = tracker.onMotionEvent(
            action = MotionEvent.ACTION_UP,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 7.0,
            currentTilt = 10.0,
        )

        // Then
        onUp.didEndInteraction shouldBe true
        onUp.bearing shouldBe null
        onUp.tilt shouldBe null

        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
            currentTilt = 0.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
            currentTilt = 0.0,
        )

        // When
        val onCancel = tracker.onMotionEvent(
            action = MotionEvent.ACTION_CANCEL,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 7.0,
            currentTilt = 10.0,
        )

        // Then
        onCancel.didEndInteraction shouldBe true
        onCancel.bearing shouldBe null
        onCancel.tilt shouldBe null
    }

    @Test
    fun `normalizeBearing should wrap values into zero to three hundred sixty degrees`() {
        // Given
        val negativeBearing = -10.0
        val overflowBearing = 370.0

        // When
        val normalizedNegative = normalizeBearing(negativeBearing)
        val normalizedOverflow = normalizeBearing(overflowBearing)

        // Then
        normalizedNegative shouldBe 350.0
        normalizedOverflow shouldBe 10.0
    }
}
