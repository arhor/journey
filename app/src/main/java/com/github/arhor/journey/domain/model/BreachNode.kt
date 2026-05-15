package com.github.arhor.journey.domain.model

data class BreachNode(
    val definition: BreachNodeDefinition,
    val state: BreachNodeState?,
    val phase: BreachNodePhase,
    val distanceMeters: Double?,
    val canDiscover: Boolean,
    val canStartUpload: Boolean,
)
