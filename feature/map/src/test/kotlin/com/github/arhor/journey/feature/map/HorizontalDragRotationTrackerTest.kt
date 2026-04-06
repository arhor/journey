package com.github.arhor.journey.feature.map

import android.view.MotionEvent
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class HorizontalDragRotationTrackerTest {

    @Test
    fun `onMotionEvent should start rotating only after a horizontal drag threshold is exceeded`() {
        // Given
        val tracker = HorizontalDragRotationTracker()

        // When
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 100f,
            y = 100f,
            pointerCount = 1,
            currentBearing = 0.0,
        )
        val belowThreshold = tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 108f,
            y = 101f,
            pointerCount = 1,
            currentBearing = 0.0,
        )
        val aboveThreshold = tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 120f,
            y = 102f,
            pointerCount = 1,
            currentBearing = 0.0,
        )

        // Then
        belowThreshold shouldBe HorizontalDragRotationUpdate()
        aboveThreshold.didStartInteraction shouldBe true
        aboveThreshold.bearing shouldBe (1.44 plusOrMinus 0.001)
    }

    @Test
    fun `onMotionEvent should ignore multi touch and end interaction`() {
        // Given
        val tracker = HorizontalDragRotationTracker()

        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 50f,
            y = 50f,
            pointerCount = 1,
            currentBearing = 10.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 70f,
            y = 50f,
            pointerCount = 1,
            currentBearing = 10.0,
        )

        // When
        val actual = tracker.onMotionEvent(
            action = MotionEvent.ACTION_POINTER_DOWN,
            x = 70f,
            y = 50f,
            pointerCount = 2,
            currentBearing = 17.0,
        )

        // Then
        actual.didEndInteraction shouldBe true
        actual.bearing shouldBe null
    }

    @Test
    fun `onMotionEvent should end interaction on finger lift and cancellation`() {
        // Given
        val tracker = HorizontalDragRotationTracker()

        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
        )

        // When
        val onUp = tracker.onMotionEvent(
            action = MotionEvent.ACTION_UP,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 7.0,
        )

        // Then
        onUp.didEndInteraction shouldBe true
        onUp.bearing shouldBe null

        tracker.onMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 20f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
        )
        tracker.onMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 0.0,
        )

        // When
        val onCancel = tracker.onMotionEvent(
            action = MotionEvent.ACTION_CANCEL,
            x = 40f,
            y = 20f,
            pointerCount = 1,
            currentBearing = 7.0,
        )

        // Then
        onCancel.didEndInteraction shouldBe true
        onCancel.bearing shouldBe null
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
