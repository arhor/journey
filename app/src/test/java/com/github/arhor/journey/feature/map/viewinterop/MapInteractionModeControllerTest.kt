package com.github.arhor.journey.feature.map.viewinterop

import com.github.arhor.journey.feature.map.MapMode
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapInteractionModeControllerTest {

    @Test
    fun `apply should enable standard gestures for exploration mode`() {
        // Given
        val target = RecordingMapInteractionTarget()
        val controller = MapInteractionModeController()

        // When
        controller.apply(
            mode = MapMode.Exploration(
                styleUri = "asset://map/styles/light.json",
            ),
            target = target,
        )

        // Then
        target.lastConfig shouldBe MapInteractionConfig(
            areScrollGesturesEnabled = true,
            areHorizontalScrollGesturesEnabled = true,
            areRotateGesturesEnabled = true,
            areTiltGesturesEnabled = true,
            areZoomGesturesEnabled = true,
            isPlayerCenteredDragGestureEnabled = false,
            followMode = MapFollowMode.NONE,
        )
    }

    @Test
    fun `apply should disable pan and require tactical follow controls for breach mode`() {
        // Given
        val target = RecordingMapInteractionTarget()
        val controller = MapInteractionModeController()

        // When
        controller.apply(
            mode = MapMode.BreachTactical(
                styleUri = "asset://map/styles/cyberpunk.json",
                isLocationAvailable = true,
            ),
            target = target,
        )

        // Then
        target.lastConfig shouldBe MapInteractionConfig(
            areScrollGesturesEnabled = false,
            areHorizontalScrollGesturesEnabled = false,
            areRotateGesturesEnabled = false,
            areTiltGesturesEnabled = false,
            areZoomGesturesEnabled = true,
            isPlayerCenteredDragGestureEnabled = true,
            followMode = MapFollowMode.USER_TRACKING,
        )
    }

    @Test
    fun `apply should avoid reapplying the same mode twice`() {
        // Given
        val target = RecordingMapInteractionTarget()
        val controller = MapInteractionModeController()
        val mode = MapMode.Exploration(
            styleUri = "asset://map/styles/light.json",
        )

        // When
        controller.apply(mode = mode, target = target)
        controller.apply(mode = mode, target = target)

        // Then
        target.applyCount shouldBe 1
    }

    private class RecordingMapInteractionTarget : MapInteractionTarget {
        var lastConfig: MapInteractionConfig? = null
        var applyCount: Int = 0

        override fun applyInteractionConfig(config: MapInteractionConfig) {
            lastConfig = config
            applyCount += 1
        }
    }
}
