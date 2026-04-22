package com.github.arhor.journey.feature.map.fow.model

const val ACTIVE_FOG_OF_WAR_SOURCE_ID = "fog-of-war-source-active"
const val ACTIVE_FOG_OF_WAR_LAYER_ID = "fog-of-war-layer-active"
const val HANDOFF_FOG_OF_WAR_SOURCE_ID = "fog-of-war-source-handoff"
const val HANDOFF_FOG_OF_WAR_LAYER_ID = "fog-of-war-layer-handoff"
const val HIDDEN_EXPLORED_SOURCE_ID = "fog-of-war-source-hidden-explored"
const val HIDDEN_EXPLORED_LAYER_ID = "fog-of-war-layer-hidden-explored"
const val ACTIVE_FOG_OF_WAR_OPACITY = 0.90f
const val HIDDEN_EXPLORED_OPACITY = 0.40f

fun FogOfWarRenderState.fogOfWarLayerSpecs(): List<FogOfWarLayerSpec> = listOf(
    FogOfWarLayerSpec(
        renderData = hiddenExploredRenderData,
        sourceId = HIDDEN_EXPLORED_SOURCE_ID,
        layerId = HIDDEN_EXPLORED_LAYER_ID,
        isVisible = hiddenExploredRenderData != null,
        opacity = HIDDEN_EXPLORED_OPACITY,
    ),
    FogOfWarLayerSpec(
        renderData = handoffRenderData,
        sourceId = HANDOFF_FOG_OF_WAR_SOURCE_ID,
        layerId = HANDOFF_FOG_OF_WAR_LAYER_ID,
        isVisible = handoffRenderData != null,
        opacity = ACTIVE_FOG_OF_WAR_OPACITY,
    ),
    FogOfWarLayerSpec(
        renderData = activeRenderData,
        sourceId = ACTIVE_FOG_OF_WAR_SOURCE_ID,
        layerId = ACTIVE_FOG_OF_WAR_LAYER_ID,
        isVisible = activeRenderData != null,
        opacity = ACTIVE_FOG_OF_WAR_OPACITY,
    ),
)

data class FogOfWarLayerSpec(
    val renderData: FogOfWarRenderData?,
    val sourceId: String,
    val layerId: String,
    val isVisible: Boolean,
    val opacity: Float,
)
