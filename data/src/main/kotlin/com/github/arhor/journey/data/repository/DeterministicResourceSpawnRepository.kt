package com.github.arhor.journey.data.repository

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.floor

private const val GENERATOR_VERSION = 1
private const val CELL_SIZE_DEGREES = 0.005
private const val SPAWNS_PER_CELL = 2
private const val COLLECTION_RADIUS_METERS = 25.0
private const val CELL_PADDING_FRACTION = 0.18
private const val HASH_FRACTION_GRANULARITY = 10_000.0
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

@Singleton
class DeterministicResourceSpawnRepository @Inject constructor() : ResourceSpawnRepository {

    override fun observeActiveSpawns(query: ResourceSpawnQuery): Flow<List<ResourceSpawn>> = flow {
        emit(getActiveSpawns(query))
    }.flowOn(Dispatchers.Default)

    override suspend fun getActiveSpawns(query: ResourceSpawnQuery): List<ResourceSpawn> {
        val searchBounds = query.toSearchBounds() ?: return emptyList()
        val day = query.at.toUtcDate()
        val coroutineContext = currentCoroutineContext()

        return buildList {
            val minCellX = floor(searchBounds.west / CELL_SIZE_DEGREES).toLong()
            val maxCellX = floor(searchBounds.east / CELL_SIZE_DEGREES).toLong()
            val minCellY = floor(searchBounds.south / CELL_SIZE_DEGREES).toLong()
            val maxCellY = floor(searchBounds.north / CELL_SIZE_DEGREES).toLong()

            for (cellY in minCellY..maxCellY) {
                coroutineContext.ensureActive()
                for (cellX in minCellX..maxCellX) {
                    coroutineContext.ensureActive()
                    for (slot in 0 until SPAWNS_PER_CELL) {
                        coroutineContext.ensureActive()
                        val spawn = generateSpawn(
                            day = day,
                            cellX = cellX,
                            cellY = cellY,
                            slot = slot,
                        )

                        if (spawn.matches(query)) {
                            add(spawn)
                        }
                    }
                }
            }
        }.sortedBy(ResourceSpawn::id)
    }

    override suspend fun getActiveSpawn(
        spawnId: String,
        at: Instant,
    ): ResourceSpawn? {
        val descriptor = parseSpawnId(spawnId) ?: return null
        if (descriptor.day != at.toUtcDate()) {
            return null
        }

        val spawn = generateSpawn(
            day = descriptor.day,
            cellX = descriptor.cellX,
            cellY = descriptor.cellY,
            slot = descriptor.slot,
        )

        return spawn.takeIf { candidate ->
            candidate.id == spawnId &&
                candidate.typeId == descriptor.resourceTypeId &&
                candidate.isActiveAt(at)
        }
    }

    private fun generateSpawn(
        day: LocalDate,
        cellX: Long,
        cellY: Long,
        slot: Int,
    ): ResourceSpawn {
        val daySeed = day.toEpochDay()
        val resourceType = ResourceType.generationOrderedEntries[
            stableIndex("type:$GENERATOR_VERSION:$daySeed:$cellX:$cellY:$slot", ResourceType.generationOrderedEntries.size)
        ]
        val seedPrefix = "$GENERATOR_VERSION:$daySeed:$cellX:$cellY:$slot:${resourceType.generationTypeId}"
        val cellSouth = cellY * CELL_SIZE_DEGREES
        val cellWest = cellX * CELL_SIZE_DEGREES
        val innerFraction = 1.0 - (CELL_PADDING_FRACTION * 2.0)
        val latitude = cellSouth + CELL_SIZE_DEGREES * (
            CELL_PADDING_FRACTION + (innerFraction * stableFraction("$seedPrefix:lat"))
        )
        val longitude = cellWest + CELL_SIZE_DEGREES * (
            CELL_PADDING_FRACTION + (innerFraction * stableFraction("$seedPrefix:lon"))
        )
        val availableFrom = day.atStartOfDay().toInstant(ZoneOffset.UTC)
        val availableUntil = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        return ResourceSpawn(
            id = buildSpawnId(
                day = day,
                cellX = cellX,
                cellY = cellY,
                slot = slot,
                resourceTypeId = resourceType.typeId,
            ),
            typeId = resourceType.typeId,
            position = GeoPoint(
                lat = latitude,
                lon = longitude,
            ),
            collectionRadiusMeters = COLLECTION_RADIUS_METERS,
            availableFrom = availableFrom,
            availableUntil = availableUntil,
        )
    }

    private fun buildSpawnId(
        day: LocalDate,
        cellX: Long,
        cellY: Long,
        slot: Int,
        resourceTypeId: String,
    ): String =
        "resource-spawn:v$GENERATOR_VERSION:${day.toEpochDay()}:$cellX:$cellY:$slot:$resourceTypeId"

    private fun parseSpawnId(spawnId: String): SpawnDescriptor? {
        val parts = spawnId.split(':')
        if (parts.size != 7 || parts[0] != "resource-spawn") {
            return null
        }

        val version = parts[1].removePrefix("v").toIntOrNull() ?: return null
        if (version != GENERATOR_VERSION) {
            return null
        }

        val epochDay = parts[2].toLongOrNull() ?: return null
        val cellX = parts[3].toLongOrNull() ?: return null
        val cellY = parts[4].toLongOrNull() ?: return null
        val slot = parts[5].toIntOrNull() ?: return null
        val resourceTypeId = parts[6]

        return SpawnDescriptor(
            day = LocalDate.ofEpochDay(epochDay),
            cellX = cellX,
            cellY = cellY,
            slot = slot,
            resourceTypeId = resourceTypeId,
        )
    }

    private fun ResourceSpawnQuery.toSearchBounds(): GeoBounds? {
        val queryCenter = center
        val queryRadiusMeters = radiusMeters
        val queryBounds = bounds
        val centerBounds = queryCenter?.let { location ->
            val radius = queryRadiusMeters ?: 0.0
            location.toBounds(radius)
        }

        return when {
            queryBounds != null && centerBounds != null -> queryBounds.intersect(centerBounds)
            queryBounds != null -> queryBounds
            centerBounds != null -> centerBounds
            else -> null
        }
    }

    private fun GeoPoint.toBounds(radiusMeters: Double): GeoBounds {
        val latitudeOffsetDegrees = radiusMeters / METERS_PER_LATITUDE_DEGREE
        val longitudeMetersPerDegree = (
            METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(lat)).absoluteValue
        ).coerceAtLeast(1.0)
        val longitudeOffsetDegrees = radiusMeters / longitudeMetersPerDegree

        return GeoBounds(
            south = lat - latitudeOffsetDegrees,
            west = lon - longitudeOffsetDegrees,
            north = lat + latitudeOffsetDegrees,
            east = lon + longitudeOffsetDegrees,
        )
    }

    private fun ResourceSpawn.matches(query: ResourceSpawnQuery): Boolean {
        val queryBounds = query.bounds
        val queryCenter = query.center
        val queryRadiusMeters = query.radiusMeters

        if (!isActiveAt(query.at)) {
            return false
        }
        if (queryBounds != null && !queryBounds.contains(position)) {
            return false
        }
        if (queryCenter != null && queryRadiusMeters != null) {
            return position.distanceTo(queryCenter) <= queryRadiusMeters
        }

        return true
    }

    private fun ResourceSpawn.isActiveAt(instant: Instant): Boolean {
        val startsBeforeOrAtInstant = availableFrom?.let { !instant.isBefore(it) } ?: true
        val endsAfterInstant = availableUntil?.let { instant.isBefore(it) } ?: true
        return startsBeforeOrAtInstant && endsAfterInstant
    }

    private fun stableFraction(seed: String): Double =
        stablePositiveHash(seed).toDouble() / HASH_FRACTION_GRANULARITY

    private fun stableIndex(
        seed: String,
        size: Int,
    ): Int = (stablePositiveHash(seed).toInt() % size)

    private fun stablePositiveHash(seed: String): Long =
        (seed.hashCode().toLong() and 0x7fffffffL) % HASH_FRACTION_GRANULARITY.toLong()

    private fun Instant.toUtcDate(): LocalDate = atZone(ZoneOffset.UTC).toLocalDate()

    private data class SpawnDescriptor(
        val day: LocalDate,
        val cellX: Long,
        val cellY: Long,
        val slot: Int,
        val resourceTypeId: String,
    )
}
