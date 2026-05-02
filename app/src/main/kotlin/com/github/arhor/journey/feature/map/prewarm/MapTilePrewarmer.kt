package com.github.arhor.journey.feature.map.prewarm

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.MapViewportSize
import kotlinx.coroutines.Job
import kotlin.time.Duration

data class MapTilePrewarmRequest(
    val requestKey: String,
    val style: MapStyle,
    val currentCamera: CameraPositionState?,
    val targetCamera: CameraPositionState,
    val currentVisibleBounds: GeoBounds?,
    val viewportSize: MapViewportSize?,
    val animationDuration: Duration,
    val sampleCount: Int,
    val burstLimit: Int,
)

interface MapTilePrewarmer {
    fun prewarm(request: MapTilePrewarmRequest): Job
}
