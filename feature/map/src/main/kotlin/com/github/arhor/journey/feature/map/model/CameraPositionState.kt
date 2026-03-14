package com.github.arhor.journey.feature.map.model

import androidx.compose.runtime.Immutable

@Immutable
data class CameraPositionState(
    val target: LatLng,
    val zoom: Double,
)
