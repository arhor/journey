package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.error.UseCaseError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetExplorationTileRuntimeConfigUseCase @Inject constructor(
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    operator fun invoke(): Output<ExplorationTileRuntimeConfig, UseCaseError> =
        runUseCaseCatching("get exploration tile runtime config") {
            configHolder.snapshot()
        }
}
