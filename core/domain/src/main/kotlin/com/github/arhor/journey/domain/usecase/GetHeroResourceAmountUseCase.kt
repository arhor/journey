package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetHeroResourceAmountUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val heroInventoryRepository: HeroInventoryRepository,
) {
    suspend operator fun invoke(resourceTypeId: String): Int {
        val hero = heroRepository.getCurrentHero()

        return heroInventoryRepository.getAmount(
            heroId = hero.id,
            resourceTypeId = resourceTypeId,
        )
    }
}
