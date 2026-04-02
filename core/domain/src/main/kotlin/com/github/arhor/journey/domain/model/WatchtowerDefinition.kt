package com.github.arhor.journey.domain.model

data class WatchtowerDefinition(
    val id: String,
    val name: String,
    val description: String?,
    val location: GeoPoint,
    val interactionRadiusMeters: Double,
)
