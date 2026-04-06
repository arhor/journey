package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveHeroUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
) {
    operator fun invoke(): Flow<Output<Hero, UseCaseError>> =
        heroRepository.observeCurrentHero().toUseCaseOutputFlow("observe hero")
}
