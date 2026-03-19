package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddHeroResourceUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val heroInventoryRepository: HeroInventoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        resourceTypeId: String,
        amount: Int = 1,
    ): HeroResource {
        require(amount > 0) { "Added resource amount must be greater than zero." }

        val hero = heroRepository.getCurrentHero()

        return heroInventoryRepository.addAmount(
            heroId = hero.id,
            resourceTypeId = resourceTypeId,
            amount = amount,
            updatedAt = clock.instant(),
        )
    }
}
