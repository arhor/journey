package com.github.arhor.journey.domain.map.model

sealed interface ResolvedMapStyle {
    data class Uri(val value: String) : ResolvedMapStyle
    data class Json(val value: String) : ResolvedMapStyle
}
