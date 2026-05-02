package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.error.HeroResourcesError
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import kotlinx.coroutines.CancellationException
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
        if (amount <= 0) {
            return Output.Failure(
                HeroResourcesError.InvalidAmount(
                    message = "Spent resource amount must be greater than zero.",
                ),
            )
        }

        return try {
            val hero = heroRepository.getCurrentHero()
            val spentResource = heroInventoryRepository.spendAmount(
                heroId = hero.id,
                resourceTypeId = resourceTypeId,
                amount = amount,
                updatedAt = clock.instant(),
            )

            if (spentResource != null) {
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
        } catch (exception: Throwable) {
            if (exception is CancellationException) {
                throw exception
            }

            Output.Failure(HeroResourcesError.Unexpected(exception))
        }
    }
}
