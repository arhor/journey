package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.player.model.HeroEnergy
import com.github.arhor.journey.domain.player.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import java.time.Instant

fun HeroEntity.toDomain(): Hero =
    Hero(
        id = id,
        name = name,
        stats = HeroStats(
            strength = strength,
            vitality = vitality,
            dexterity = dexterity,
            stamina = stamina,
        ),
        progression = Progression(
            level = level,
            xpInLevel = xpInLevel,
        ),
        energy = HeroEnergy(
            current = energyCurrent,
            max = energyMax,
        ),
        createdAt = Instant.ofEpochMilli(createdAtMs),
        updatedAt = Instant.ofEpochMilli(updatedAtMs),
    )

fun Hero.toEntity(): HeroEntity =
    HeroEntity(
        id = id,
        name = name,
        level = progression.level,
        xpInLevel = progression.xpInLevel,
        strength = stats.strength,
        vitality = stats.vitality,
        dexterity = stats.dexterity,
        stamina = stats.stamina,
        energyCurrent = energy.current,
        energyMax = energy.max,
        createdAtMs = createdAt.toEpochMilli(),
        updatedAtMs = updatedAt.toEpochMilli(),
    )

