package com.github.arhor.journey.domain.map.model

sealed interface MapResolvedStyle {
    data class Uri(val value: String) : MapResolvedStyle
    data class Json(val value: String) : MapResolvedStyle
}
