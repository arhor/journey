package com.github.arhor.journey.domain.exploration.usecase

import com.github.arhor.journey.domain.exploration.model.ExplorationProgress
import com.github.arhor.journey.domain.exploration.repository.ExplorationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExplorationProgressUseCase @Inject constructor(
    private val repository: ExplorationRepository,
) {
    operator fun invoke(): Flow<ExplorationProgress> = repository.observeProgress()
}
