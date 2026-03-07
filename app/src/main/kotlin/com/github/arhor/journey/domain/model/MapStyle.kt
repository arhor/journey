package com.github.arhor.journey.domain.model

enum class MapStyle {
    DEFAULT,
    CLASSIC,
    DARK,
    SATELLITE,
    TERRAIN,
    ;

    companion object {
        fun fromName(value: String?): MapStyle =
            value
                ?.let { runCatching { valueOf(it) }.getOrNull() }
                ?: DEFAULT
    }
}
