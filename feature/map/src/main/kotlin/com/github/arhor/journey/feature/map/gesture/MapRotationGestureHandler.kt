package com.github.arhor.journey.feature.map.gesture

import android.view.MotionEvent
import kotlin.math.abs

internal data class PlayerCenteredCameraGestureUpdate(
    val bearing: Double? = null,
    val tilt: Double? = null,
    val didStartInteraction: Boolean = false,
    val didEndInteraction: Boolean = false,
)

internal class PlayerCenteredCameraGestureTracker(
    private val dragThresholdPx: Float = 12f,
    private val bearingDegreesPerPixel: Double,
    private val tiltDegreesPerPixel: Double,
    private val minTilt: Double,
    private val maxTilt: Double,
) {
    private var activePointerCount: Int = 0
    private var startX: Float? = null
    private var startY: Float? = null
    private var startBearing: Double = 0.0
    private var startTilt: Double = 0.0
    private var totalDx = 0f
    private var totalDy = 0f
    private var isAdjustingCamera = false

    fun onMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        pointerCount: Int,
        currentBearing: Double,
        currentTilt: Double,
    ): PlayerCenteredCameraGestureUpdate = when (action) {
        MotionEvent.ACTION_DOWN -> {
            activePointerCount = pointerCount
            startX = x
            startY = y
            startBearing = currentBearing
            startTilt = currentTilt
            totalDx = 0f
            totalDy = 0f
            isAdjustingCamera = false
            PlayerCenteredCameraGestureUpdate()
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            activePointerCount = pointerCount
            endInteraction()
        }

        MotionEvent.ACTION_MOVE -> {
            if (pointerCount != 1 || activePointerCount != 1) {
                activePointerCount = pointerCount
                return endInteraction()
            }

            val originX = startX ?: x
            val originY = startY ?: y
            totalDx = x - originX
            totalDy = y - originY

            val didStartInteraction = !isAdjustingCamera
            if (!isAdjustingCamera) {
                val exceedsThreshold = abs(totalDx) >= dragThresholdPx || abs(totalDy) >= dragThresholdPx
                if (!exceedsThreshold) {
                    return PlayerCenteredCameraGestureUpdate()
                }
                isAdjustingCamera = true
            }

            PlayerCenteredCameraGestureUpdate(
                bearing = normalizeBearing(startBearing + totalDx * bearingDegreesPerPixel),
                tilt = (startTilt - totalDy * tiltDegreesPerPixel).coerceIn(minTilt, maxTilt),
                didStartInteraction = didStartInteraction,
            )
        }

        MotionEvent.ACTION_POINTER_UP,
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            activePointerCount = maxOf(0, pointerCount - 1)
            endInteraction()
        }

        else -> PlayerCenteredCameraGestureUpdate()
    }

    private fun endInteraction(): PlayerCenteredCameraGestureUpdate {
        val didEndInteraction = isAdjustingCamera
        startX = null
        startY = null
        totalDx = 0f
        totalDy = 0f
        isAdjustingCamera = false
        return PlayerCenteredCameraGestureUpdate(didEndInteraction = didEndInteraction)
    }
}

internal fun normalizeBearing(bearing: Double): Double {
    val normalized = bearing % 360.0
    return if (normalized < 0.0) {
        normalized + 360.0
    } else {
        normalized
    }
}
