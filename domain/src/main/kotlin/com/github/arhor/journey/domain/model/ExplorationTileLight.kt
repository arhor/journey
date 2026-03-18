package com.github.arhor.journey.domain.model

data class ExplorationTileLight(
    val tile: ExplorationTile,
    val light: Float,
) {
    init {
        require(light in MIN_LIGHT..MAX_LIGHT) { "light must be in [$MIN_LIGHT, $MAX_LIGHT]" }
    }

    companion object {
        const val MIN_LIGHT: Float = 0.0f
        const val MAX_LIGHT: Float = 1.0f
    }
}
