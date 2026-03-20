package com.github.arhor.journey.core.common

enum class ResourceType(
    val typeId: String,
    val drawableName: String,
) {
    WOOD(
        typeId = "wood",
        drawableName = "wood",
    ),
    COAL(
        typeId = "coal",
        drawableName = "coal",
    ),
    STONE(
        typeId = "stone",
        drawableName = "stone",
    ),
    ;

    companion object {
        val entriesById = entries.associateBy { it.typeId }

        fun fromTypeId(typeId: String): ResourceType? = entriesById[typeId]
    }
}
