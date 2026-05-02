package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.CollectedResourceSpawnEntity
import com.github.arhor.journey.data.local.db.entity.HeroResourceEntity
import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import com.github.arhor.journey.domain.model.HeroResource

fun HeroResourceEntity.toDomain(): HeroResource =
    HeroResource(
        heroId = heroId,
        resourceTypeId = typeId,
        amount = amount,
        updatedAt = updatedAt,
    )

fun HeroResource.toEntity(): HeroResourceEntity =
    HeroResourceEntity(
        heroId = heroId,
        typeId = resourceTypeId,
        amount = amount,
        updatedAt = updatedAt,
    )

fun CollectedResourceSpawnEntity.toDomain(): CollectedResourceSpawn =
    CollectedResourceSpawn(
        heroId = heroId,
        typeId = typeId,
        spawnId = spawnId,
        collectedAt = collectedAt,
    )

fun CollectedResourceSpawn.toEntity(): CollectedResourceSpawnEntity =
    CollectedResourceSpawnEntity(
        heroId = heroId,
        typeId = typeId,
        spawnId = spawnId,
        collectedAt = collectedAt,
    )
