package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.feature.map.BreachDirectionalGuidanceUiState
import javax.inject.Inject

class BreachDirectionalGuidancePresenter @Inject constructor() {

    fun present(
        breach: BreachNode,
        actorLocation: GeoPoint?,
    ): BreachDirectionalGuidanceUiState {
        if (actorLocation == null) {
            return BreachDirectionalGuidanceUiState.Unavailable(
                breachNodeId = breach.definition.id,
                districtName = breach.definition.districtName,
                message = "Location required to continue breach scan.",
            )
        }

        val distanceMeters = actorLocation.distanceTo(breach.definition.location)
        val isInUploadRange = distanceMeters <= breach.definition.interactionRadiusMeters

        return if (isInUploadRange) {
            BreachDirectionalGuidanceUiState.OnTarget(
                breachNodeId = breach.definition.id,
                districtName = breach.definition.districtName,
                distanceMeters = distanceMeters.toInt(),
                canStartUpload = isInUploadRange,
            )
        } else {
            BreachDirectionalGuidanceUiState.FloatingArrow(
                breachNodeId = breach.definition.id,
                districtName = breach.definition.districtName,
                bearingDegrees = actorLocation.bearingTo(breach.definition.location),
                distanceMeters = distanceMeters.toInt(),
                canStartUpload = isInUploadRange,
            )
        }
    }
}
