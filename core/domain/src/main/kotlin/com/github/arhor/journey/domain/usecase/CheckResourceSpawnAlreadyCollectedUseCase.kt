package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckResourceSpawnAlreadyCollectedUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val collectedResourceSpawnRepository: CollectedResourceSpawnRepository,
) {
    suspend operator fun invoke(spawnId: String): Output<Boolean, UseCaseError> =
        runSuspendingUseCaseCatching("check resource spawn collection status") {
            val hero = heroRepository.getCurrentHero()

            collectedResourceSpawnRepository.isCollected(
                heroId = hero.id,
                spawnId = spawnId,
            )
        }
}
