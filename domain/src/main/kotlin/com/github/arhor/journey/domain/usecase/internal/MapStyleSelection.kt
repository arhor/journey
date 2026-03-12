package com.github.arhor.journey.domain.usecase.internal

import com.github.arhor.journey.domain.model.MapStyle

internal fun List<MapStyle>.resolveMapStyleId(mapStyleId: String?): String? {
    return when {
        mapStyleId == null -> {
            firstOrNull()?.id
        }

        any { it.id == mapStyleId } -> {
            mapStyleId
        }

        else -> {
            firstOrNull()?.id
        }
    }
}
