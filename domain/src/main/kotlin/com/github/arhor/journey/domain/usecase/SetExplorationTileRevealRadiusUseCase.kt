package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetExplorationTileRevealRadiusUseCase @Inject constructor(
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    operator fun invoke(revealRadiusMeters: Double) {
        configHolder.setRevealRadiusMeters(revealRadiusMeters)
    }
}
