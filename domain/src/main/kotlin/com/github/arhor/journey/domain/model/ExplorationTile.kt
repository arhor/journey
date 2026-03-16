package com.github.arhor.journey.domain.model

data class ExplorationTile(
    val zoom: Int,
    val x: Int,
    val y: Int,
) {
    init {
        require(zoom >= 0) { "zoom must be >= 0" }
        require(x >= 0) { "x must be >= 0" }
        require(y >= 0) { "y must be >= 0" }
    }
}
