package com.github.arhor.journey.domain.model

data class MapStyle(
    val id: String,
    val name: String,
    val type: Type,
    val value: String,
) {
    enum class Type {
        BUNDLE,
        REMOTE,
    }

    companion object {
        val defaultStyle = bundle(
            id = "cyberpunk",
            name = "Cyberpunk",
            value = "asset://map/styles/cyberpunk.json",
        )

        val availableStyles = listOf(
            defaultStyle,
            bundle(
                id = "light",
                name = "Light",
                value = "asset://map/styles/light.json",
            ),
            bundle(
                id = "urban-noir",
                name = "Urban Noir",
                value = "asset://map/styles/urban-noir.json",
            ),
        )

        fun bundle(id: String, name: String, value: String) =
            MapStyle(id = id, name = name, type = Type.BUNDLE, value = value)

        fun remote(id: String, name: String, value: String) =
            MapStyle(id = id, name = name, type = Type.REMOTE, value = value)

        fun styleById(id: String): MapStyle? =
            availableStyles.firstOrNull { it.id == id }
    }
}
