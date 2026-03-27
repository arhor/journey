package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckResourceSpawnAlreadyCollectedUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val collectedResourceSpawnRepository: CollectedResourceSpawnRepository,
) {
    suspend operator fun invoke(spawnId: String): Boolean {
        val hero = heroRepository.getCurrentHero()

        return collectedResourceSpawnRepository.isCollected(
            heroId = hero.id,
            spawnId = spawnId,
        )
    }
}
