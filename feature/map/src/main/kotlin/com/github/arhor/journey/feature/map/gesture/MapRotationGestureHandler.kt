package com.github.arhor.journey.feature.map.gesture

import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.motionEventSpy
import com.github.arhor.journey.feature.map.camera.toCameraPositionState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import org.maplibre.compose.camera.CameraState
import kotlin.math.abs

internal fun Modifier.mapRotationGestureHandler(
    cameraState: CameraState,
    onGestureStarted: (CameraPositionState) -> Unit,
    onCameraSettled: (CameraPositionState, CameraUpdateOrigin) -> Unit,
): Modifier = composed {
    val tracker = remember {
        HorizontalDragRotationTracker()
    }
    val latestOnGestureStarted by rememberUpdatedState(onGestureStarted)
    val latestOnCameraSettled by rememberUpdatedState(onCameraSettled)

    motionEventSpy { motionEvent ->
        val currentPosition = cameraState.position
        val update = tracker.onMotionEvent(
            action = motionEvent.actionMasked,
            x = motionEvent.x,
            y = motionEvent.y,
            pointerCount = motionEvent.pointerCount,
            currentBearing = currentPosition.bearing,
        )

        if (update.didStartInteraction || update.bearing != null) {
            latestOnGestureStarted(
                currentPosition.copy(
                    bearing = update.bearing ?: currentPosition.bearing,
                ).toCameraPositionState(),
            )
        }

        if (update.bearing != null) {
            cameraState.position = currentPosition.copy(
                target = currentPosition.target,
                bearing = update.bearing,
            )
        }

        if (update.didEndInteraction) {
            latestOnCameraSettled(
                cameraState.position.toCameraPositionState(),
                CameraUpdateOrigin.USER,
            )
        }
    }
}

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
