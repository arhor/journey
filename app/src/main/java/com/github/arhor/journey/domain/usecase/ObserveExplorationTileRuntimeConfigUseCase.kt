package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.error.UseCaseError
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExplorationTileRuntimeConfigUseCase @Inject constructor(
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    operator fun invoke(): Flow<Output<ExplorationTileRuntimeConfig, UseCaseError>> =
        configHolder.observe().toUseCaseOutputFlow("observe exploration tile runtime config")
}
