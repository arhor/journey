package com.github.arhor.journey.domain.model

data class WatchtowerRecord(
    val definition: WatchtowerDefinition,
    val state: WatchtowerState?,
)
