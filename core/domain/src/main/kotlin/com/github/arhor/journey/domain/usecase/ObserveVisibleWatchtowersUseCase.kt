package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.internal.toWatchtower
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveVisibleWatchtowersUseCase @Inject constructor(
    private val repository: WatchtowerRepository,
) {
    operator fun invoke(
        bounds: GeoBounds,
    ): Flow<Output<List<Watchtower>, UseCaseError>> =
        repository.observeInBounds(bounds)
            .map { records -> records.mapNotNull { it.toWatchtower() } }
            .toUseCaseOutputFlow("observe visible watchtowers")
}
