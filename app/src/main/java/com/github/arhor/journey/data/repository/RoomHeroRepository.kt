package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.seed.DefaultHeroSeed
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHeroRepository @Inject constructor(
    private val clock: Clock,
) : HeroRepository {
    private val mutex = Mutex()
    private val currentHero = MutableStateFlow<Hero?>(null)

    override fun observeCurrentHero(): Flow<Hero> =
        currentHero
            .also { ensureSeededIfMissing() }
            .filterNotNull()

    override suspend fun getCurrentHero(): Hero {
        ensureSeeded()
        return requireNotNull(currentHero.value) {
            "Default hero must exist after initialization."
        }
    }

    override suspend fun upsert(hero: Hero) {
        mutex.withLock {
            currentHero.value = hero
        }
    }

    private suspend fun ensureSeeded() {
        mutex.withLock {
            if (currentHero.value == null) {
                currentHero.value = DefaultHeroSeed.create(clock.instant())
            }
        }
    }

    private fun ensureSeededIfMissing() {
        if (currentHero.value == null) {
            currentHero.value = DefaultHeroSeed.create(clock.instant())
        }
    }
}
