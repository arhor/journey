package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.player.model.Hero
import kotlinx.coroutines.flow.Flow

/**
 * Access to the current hero state.
 *
 * The current foundation assumes a single "current hero" stored locally, but keeps the repository
 * contract focused on domain models and reactive observation.
 */
interface HeroRepository {

    fun observeCurrentHero(): Flow<Hero>

    /**
     * Returns the current hero, creating a default one if it does not exist yet.
     */
    suspend fun getCurrentHero(): Hero

    suspend fun upsert(hero: Hero)
}
