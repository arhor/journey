package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.HeroDao
import com.github.arhor.journey.data.local.seed.DefaultHeroSeed
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHeroRepository @Inject constructor(
    private val heroDao: HeroDao,
    private val clock: Clock,
) : HeroRepository {

    override fun observeCurrentHero(): Flow<Hero> =
        heroDao.observeById(DefaultHeroSeed.CURRENT_HERO_ID)
            .onStart { ensureSeeded() }
            .filterNotNull()
            .map { it.toDomain() }

    override suspend fun getCurrentHero(): Hero {
        ensureSeeded()
        return requireNotNull(heroDao.getById(DefaultHeroSeed.CURRENT_HERO_ID)) {
            "Default hero must exist after seeding."
        }.toDomain()
    }

    override suspend fun upsert(hero: Hero) {
        heroDao.upsert(hero.toEntity())
    }

    private suspend fun ensureSeeded() {
        val existing = heroDao.getById(DefaultHeroSeed.CURRENT_HERO_ID)
        if (existing == null) {
            heroDao.upsert(DefaultHeroSeed.create(clock.instant()).toEntity())
        }
    }
}

