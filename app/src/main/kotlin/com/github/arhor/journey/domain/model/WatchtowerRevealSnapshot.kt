package com.github.arhor.journey.domain.model

data class WatchtowerRevealSnapshot(
    val tiles: Set<MapTile>,
    val revision: Int = tiles.hashCode(),
)
