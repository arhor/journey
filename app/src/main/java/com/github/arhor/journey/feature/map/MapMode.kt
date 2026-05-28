package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.MapStyle

@Immutable
sealed interface MapMode {
    val styleUri: String

    @Immutable
    data class Exploration(
        override val styleUri: String = MapStyle.styleById("light")!!.value,
    ) : MapMode

    @Immutable
    data class BreachTactical(
        override val styleUri: String = MapStyle.styleById("cyberpunk")!!.value,
        val isLocationAvailable: Boolean,
    ) : MapMode
}
