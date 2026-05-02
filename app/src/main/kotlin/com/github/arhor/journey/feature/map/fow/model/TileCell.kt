package com.github.arhor.journey.feature.map.fow.model

internal data class TileCell(
    val x: Int,
    val y: Int,
) {
    fun neighbors(): List<TileCell> = listOf(
        copy(y = y - 1),
        copy(x = x + 1),
        copy(y = y + 1),
        copy(x = x - 1),
    )
}
