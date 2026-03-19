package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression

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
            now = energyNow,
            max = energyMax,
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
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
        energyNow = energy.now,
        energyMax = energy.max,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
