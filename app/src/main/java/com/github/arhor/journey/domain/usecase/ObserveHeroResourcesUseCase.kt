package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveHeroResourcesUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val heroInventoryRepository: HeroInventoryRepository,
) {
    operator fun invoke(): Flow<Output<List<HeroResource>, UseCaseError>> =
        heroRepository.observeCurrentHero()
            .flatMapLatest { hero -> heroInventoryRepository.observeAll(hero.id) }
            .toUseCaseOutputFlow("observe hero resources")
}
