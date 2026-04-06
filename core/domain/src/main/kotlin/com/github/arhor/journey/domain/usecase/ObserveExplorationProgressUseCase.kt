package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.ExplorationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExplorationProgressUseCase @Inject constructor(
    private val repository: ExplorationRepository,
) {
    operator fun invoke(): Flow<Output<ExplorationProgress, UseCaseError>> =
        repository.observeProgress().toUseCaseOutputFlow("observe exploration progress")
}
