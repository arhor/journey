package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetExplorationTileRuntimeConfigUseCase @Inject constructor(
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    operator fun invoke(): ExplorationTileRuntimeConfig = configHolder.snapshot()
}
