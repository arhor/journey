package com.github.arhor.journey.feature.map.fow.model

import androidx.compose.runtime.Immutable

@Immutable
data class FogOfWarRenderState(
    val isOverlayEnabled: Boolean = true,
    val hiddenExploredRenderData: FogOfWarRenderData? = null,
    val activeRenderData: FogOfWarRenderData? = null,
    val handoffRenderData: FogOfWarRenderData? = null,
)
