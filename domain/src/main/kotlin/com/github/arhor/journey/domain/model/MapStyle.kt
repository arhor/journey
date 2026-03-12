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
        fun bundle(id: String, name: String, value: String) =
            MapStyle(id = id, name = name, type = Type.BUNDLE, value = value)

        fun remote(id: String, name: String, value: String) =
            MapStyle(id = id, name = name, type = Type.REMOTE, value = value)
    }
}
