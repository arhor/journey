package com.github.arhor.journey.feature.map.viewinterop

import androidx.compose.runtime.Immutable

@Immutable
internal data class NativeMapModelSpec(
    val assetPath: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val scaleMetersPerModelUnit: Double,
    val headingDegrees: Double = 0.0,
)
