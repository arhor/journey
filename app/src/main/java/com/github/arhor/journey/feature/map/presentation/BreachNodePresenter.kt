package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.feature.map.model.BreachMarkerState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import javax.inject.Inject
import kotlin.math.roundToInt

class BreachNodePresenter @Inject constructor() {

    fun present(node: BreachNode): MapObjectUiModel =
        MapObjectUiModel(
            id = node.definition.id,
            kind = MapObjectKind.BreachNode,
            title = node.definition.districtName,
            description = node.definition.description,
            position = LatLng(
                latitude = node.definition.location.lat,
                longitude = node.definition.location.lon,
            ),
            radiusMeters = node.definition.interactionRadiusMeters.roundToInt(),
            isDiscovered = node.phase != BreachNodePhase.UNDETECTED,
            markerState = markerStateFor(node),
        )

    private fun markerStateFor(node: BreachNode): BreachMarkerState =
        when {
            node.phase == BreachNodePhase.LOCKDOWN -> BreachMarkerState.LOCKDOWN
            node.phase == BreachNodePhase.CONTROLLED -> BreachMarkerState.CONTROLLED
            node.canStartUpload -> BreachMarkerState.UPLOAD_READY
            else -> BreachMarkerState.DISCOVERED
        }
}
