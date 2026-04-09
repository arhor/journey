package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.WatchtowerDefinition

data class MapObjectAreaResponse(
    val resourceSpawns: List<ResourceSpawn>,
    val watchtowerDefinitions: List<WatchtowerDefinition>,
)
