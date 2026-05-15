package com.github.arhor.journey.domain.model

data class BreachNodeDefinition(
    val id: String,
    val h3CellId: String,
    val districtName: String,
    val description: String?,
    val location: GeoPoint,
    val interactionRadiusMeters: Double,
    val controlledH3CellIds: Set<String>,
)
