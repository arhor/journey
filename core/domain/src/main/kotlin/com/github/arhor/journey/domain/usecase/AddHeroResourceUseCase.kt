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
class AddHeroResourceUseCase @Inject constructor(
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
                    message = "Added resource amount must be greater than zero.",
                ),
            )
        }

        return try {
            val hero = heroRepository.getCurrentHero()

            Output.Success(
                heroInventoryRepository.addAmount(
                    heroId = hero.id,
                    resourceTypeId = resourceTypeId,
                    amount = amount,
                    updatedAt = clock.instant(),
                ),
            )
        } catch (exception: Throwable) {
            if (exception is CancellationException) {
                throw exception
            }

            Output.Failure(HeroResourcesError.Unexpected(exception))
        }
    }
}
