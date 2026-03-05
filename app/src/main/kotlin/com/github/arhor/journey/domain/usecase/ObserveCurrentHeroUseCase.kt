package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentHeroUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
) {
    operator fun invoke(): Flow<Hero> = heroRepository.observeCurrentHero()
}

