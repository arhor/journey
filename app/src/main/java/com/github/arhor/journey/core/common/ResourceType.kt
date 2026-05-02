package com.github.arhor.journey.core.common

enum class ResourceType(
    val typeId: String,
    val drawableName: String,
    val generationTypeId: String,
    val displayName: String,
) {
    SCRAP(
        typeId = "scrap",
        drawableName = "scrap",
        generationTypeId = "wood",
        displayName = "Scrap",
    ),
    COMPONENTS(
        typeId = "components",
        drawableName = "components",
        generationTypeId = "stone",
        displayName = "Components",
    ),
    FUEL(
        typeId = "fuel",
        drawableName = "fuel",
        generationTypeId = "coal",
        displayName = "Fuel",
    ),
    ;

    companion object {
        val entriesById = entries.associateBy { it.typeId }
        val entriesByGenerationTypeId = entries.associateBy { it.generationTypeId }
        val generationOrderedEntries = listOf(SCRAP, FUEL, COMPONENTS)

        fun fromTypeId(typeId: String): ResourceType? = entriesById[typeId]

        fun fromGenerationTypeId(typeId: String): ResourceType? = entriesByGenerationTypeId[typeId]

        fun migrateTypeId(typeId: String): String = fromGenerationTypeId(typeId)?.typeId ?: typeId
    }
}
