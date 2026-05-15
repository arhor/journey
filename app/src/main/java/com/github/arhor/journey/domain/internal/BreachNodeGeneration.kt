package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.spatial.H3Grid

object BreachNodeGeneration {

    private val districtAdjectives = listOf(
        "Amber",
        "Silent",
        "Obsidian",
        "Cipher",
        "Iron",
        "Neon",
        "Fractured",
        "Static",
    )
    private val districtNouns = listOf(
        "District",
        "Grid",
        "Ward",
        "Bastion",
        "Relay",
        "Sector",
        "Spire",
        "Vault",
    )

    fun definitionForCell(
        cellId: String,
        h3Grid: H3Grid,
    ): BreachNodeDefinition? {
        if (!isOccupied(cellId = cellId)) {
            return null
        }

        return BreachNodeDefinition(
            id = breachNodeId(cellId = cellId),
            h3CellId = cellId,
            districtName = buildDistrictName(cellId = cellId),
            description = "A vulnerable infrastructure node bleeding encrypted signal noise.",
            location = deterministicLocation(cellId = cellId, h3Grid = h3Grid),
            interactionRadiusMeters = BreachBalance.INTERACTION_RADIUS_METERS,
            controlledH3CellIds = setOf(cellId),
        )
    }

    fun definitionsForCells(
        cellIds: Collection<String>,
        h3Grid: H3Grid,
    ): List<BreachNodeDefinition> =
        cellIds
            .asSequence()
            .mapNotNull { cellId -> definitionForCell(cellId = cellId, h3Grid = h3Grid) }
            .sortedBy { definition -> definition.id }
            .toList()

    private fun isOccupied(cellId: String): Boolean =
        stablePositiveHash(seed = "breach-node:v${BreachBalance.GENERATOR_VERSION}:occupied:$cellId") % 100 <
            BreachBalance.OCCUPANCY_THRESHOLD_PERCENT

    private fun breachNodeId(cellId: String): String =
        "breach-node:v${BreachBalance.GENERATOR_VERSION}:h3r${BreachBalance.H3_RESOLUTION}:$cellId"

    private fun buildDistrictName(cellId: String): String {
        val adjectiveIndex = stablePositiveHash(
            seed = "breach-node:v${BreachBalance.GENERATOR_VERSION}:district-adj:$cellId",
        ) % districtAdjectives.size
        val nounIndex = stablePositiveHash(
            seed = "breach-node:v${BreachBalance.GENERATOR_VERSION}:district-noun:$cellId",
        ) % districtNouns.size
        val suffix = stablePositiveHash(
            seed = "breach-node:v${BreachBalance.GENERATOR_VERSION}:district-suffix:$cellId",
        ) % 100

        return "${districtAdjectives[adjectiveIndex]} ${districtNouns[nounIndex]} ${suffix.toString().padStart(2, '0')}"
    }

    private fun deterministicLocation(
        cellId: String,
        h3Grid: H3Grid,
    ): GeoPoint {
        val center = h3Grid.cellCenter(cellId)
        val boundary = h3Grid.cellBoundary(cellId)
        if (boundary.isEmpty()) {
            return center
        }

        val anchorIndex = stablePositiveHash(
            seed = "breach-node:v${BreachBalance.GENERATOR_VERSION}:location-anchor:$cellId",
        ) % boundary.size
        val anchor = boundary[anchorIndex]

        val ratioSeed = stablePositiveHash(
            seed = "breach-node:v${BreachBalance.GENERATOR_VERSION}:location-ratio:$cellId",
        ) % 10_000
        val ratioSpan = BreachBalance.LOCATION_EDGE_PADDING_RATIO_MAX - BreachBalance.LOCATION_EDGE_PADDING_RATIO_MIN
        val ratio = BreachBalance.LOCATION_EDGE_PADDING_RATIO_MIN + (ratioSeed / 9_999.0) * ratioSpan

        return GeoPoint(
            lat = center.lat + ((anchor.lat - center.lat) * ratio),
            lon = center.lon + ((anchor.lon - center.lon) * ratio),
        )
    }

    private fun stablePositiveHash(seed: String): Int =
        seed.hashCode() and Int.MAX_VALUE
}
