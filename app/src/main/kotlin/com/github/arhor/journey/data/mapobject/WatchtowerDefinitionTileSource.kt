package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerDefinition

interface WatchtowerDefinitionTileSource {

    suspend fun fetchWatchtowerDefinitionsIntersectingTiles(tiles: Set<MapTile>): List<WatchtowerDefinition>
}
