package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordCollectedResourceSpawnUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val collectedResourceSpawnRepository: CollectedResourceSpawnRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        spawnId: String,
        resourceTypeId: String,
    ): Boolean {
        val hero = heroRepository.getCurrentHero()

        return collectedResourceSpawnRepository.markCollected(
            heroId = hero.id,
            spawnId = spawnId,
            resourceTypeId = resourceTypeId,
            collectedAt = clock.instant(),
        )
    }
}
