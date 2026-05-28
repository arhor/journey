package com.github.arhor.journey.feature.map.viewinterop

import com.github.arhor.journey.feature.map.MapMode

internal class MapInteractionModeController {
    private var appliedMode: MapMode? = null

    fun reset() {
        appliedMode = null
    }

    fun apply(
        mode: MapMode,
        target: MapInteractionTarget,
    ) {
        if (appliedMode == mode) {
            return
        }

        target.applyInteractionConfig(mode.toInteractionConfig())
        appliedMode = mode
    }

    private fun MapMode.toInteractionConfig(): MapInteractionConfig = when (this) {
        is MapMode.Exploration -> MapInteractionConfig(
            areScrollGesturesEnabled = true,
            areHorizontalScrollGesturesEnabled = true,
            areRotateGesturesEnabled = true,
            areTiltGesturesEnabled = true,
            areZoomGesturesEnabled = true,
            isPlayerCenteredDragGestureEnabled = false,
            followMode = MapFollowMode.NONE,
        )

        is MapMode.BreachTactical -> MapInteractionConfig(
            areScrollGesturesEnabled = false,
            areHorizontalScrollGesturesEnabled = false,
            areRotateGesturesEnabled = false,
            areTiltGesturesEnabled = false,
            areZoomGesturesEnabled = true,
            isPlayerCenteredDragGestureEnabled = true,
            followMode = if (isLocationAvailable) {
                MapFollowMode.USER_TRACKING
            } else {
                MapFollowMode.NONE
            },
        )
    }
}

internal interface MapInteractionTarget {
    fun applyInteractionConfig(config: MapInteractionConfig)
}

internal data class MapInteractionConfig(
    val areScrollGesturesEnabled: Boolean,
    val areHorizontalScrollGesturesEnabled: Boolean,
    val areRotateGesturesEnabled: Boolean,
    val areTiltGesturesEnabled: Boolean,
    val areZoomGesturesEnabled: Boolean,
    val isPlayerCenteredDragGestureEnabled: Boolean,
    val followMode: MapFollowMode,
)

internal enum class MapFollowMode {
    NONE,
    USER_TRACKING,
}
