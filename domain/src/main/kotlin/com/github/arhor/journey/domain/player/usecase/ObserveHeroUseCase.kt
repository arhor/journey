package com.github.arhor.journey.domain.player.usecase

import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.player.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveHeroUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
) {
    operator fun invoke(): Flow<Hero> = heroRepository.observeCurrentHero()
}
