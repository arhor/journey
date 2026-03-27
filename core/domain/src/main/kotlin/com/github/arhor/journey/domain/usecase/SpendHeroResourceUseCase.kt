package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.error.HeroResourcesError
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendHeroResourceUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val heroInventoryRepository: HeroInventoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        resourceTypeId: String,
        amount: Int = 1,
    ): Output<HeroResource, HeroResourcesError> {
        require(amount > 0) { "Spent resource amount must be greater than zero." }

        val hero = heroRepository.getCurrentHero()
        val spentResource = heroInventoryRepository.spendAmount(
            heroId = hero.id,
            resourceTypeId = resourceTypeId,
            amount = amount,
            updatedAt = clock.instant(),
        )

        return if (spentResource != null) {
            Output.Success(spentResource)
        } else {
            Output.Failure(
                HeroResourcesError.InsufficientAmount(
                    resourceTypeId = resourceTypeId,
                    requestedAmount = amount,
                    availableAmount = heroInventoryRepository.getAmount(
                        heroId = hero.id,
                        resourceTypeId = resourceTypeId,
                    ),
                ),
            )
        }
    }
}
