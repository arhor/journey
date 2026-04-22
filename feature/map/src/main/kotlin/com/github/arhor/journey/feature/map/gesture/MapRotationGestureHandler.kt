package com.github.arhor.journey.feature.map.gesture

import android.view.MotionEvent
import kotlin.math.abs

internal data class HorizontalDragRotationUpdate(
    val bearing: Double? = null,
    val didStartInteraction: Boolean = false,
    val didEndInteraction: Boolean = false,
)

internal class HorizontalDragRotationTracker(
    private val dragThresholdPx: Float = 12f,
    private val degreesPerPixel: Double = 0.12,
) {
    private var activePointerCount: Int = 0
    private var lastX: Float? = null
    private var lastY: Float? = null
    private var totalDx = 0f
    private var totalDy = 0f
    private var isRotating = false

    fun onMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        pointerCount: Int,
        currentBearing: Double,
    ): HorizontalDragRotationUpdate = when (action) {
        MotionEvent.ACTION_DOWN -> {
            activePointerCount = pointerCount
            lastX = x
            lastY = y
            totalDx = 0f
            totalDy = 0f
            isRotating = false
            HorizontalDragRotationUpdate()
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

            val previousX = lastX ?: x
            val previousY = lastY ?: y
            val deltaX = x - previousX
            val deltaY = y - previousY
            lastX = x
            lastY = y
            totalDx += deltaX
            totalDy += deltaY

            if (!isRotating) {
                val exceedsThreshold = abs(totalDx) >= dragThresholdPx
                val isHorizontal = abs(totalDx) > abs(totalDy)
                if (!exceedsThreshold || !isHorizontal) {
                    return HorizontalDragRotationUpdate()
                }
                isRotating = true
            }

            HorizontalDragRotationUpdate(
                bearing = normalizeBearing(currentBearing + deltaX * degreesPerPixel),
                didStartInteraction = isRotating && abs(totalDx) - abs(deltaX) < dragThresholdPx,
            )
        }

        MotionEvent.ACTION_POINTER_UP,
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
            activePointerCount = maxOf(0, pointerCount - 1)
            endInteraction()
        }

        else -> HorizontalDragRotationUpdate()
    }

    private fun endInteraction(): HorizontalDragRotationUpdate {
        val didEndInteraction = isRotating
        lastX = null
        lastY = null
        totalDx = 0f
        totalDy = 0f
        isRotating = false
        return HorizontalDragRotationUpdate(didEndInteraction = didEndInteraction)
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
