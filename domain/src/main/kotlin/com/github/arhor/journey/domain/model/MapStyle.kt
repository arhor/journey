package com.github.arhor.journey.domain.model

data class MapStyle(
    val id: String,
    val name: String,
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}

sealed interface ResolvedMapStyle {
    data class Uri(val value: String) : ResolvedMapStyle
    data class Json(val value: String) : ResolvedMapStyle
}
