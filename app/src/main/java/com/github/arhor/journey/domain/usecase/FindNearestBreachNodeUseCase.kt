package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.internal.BreachBalance
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.error.BreachNodeError
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import com.github.arhor.journey.domain.spatial.H3Grid
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class FindNearestBreachNodeUseCase @Inject constructor(
    private val repository: BreachNodeRepository,
    private val h3Grid: H3Grid,
) {

    suspend operator fun invoke(actorLocation: GeoPoint): Output<BreachNodeRecord, BreachNodeError> {
        return try {
            val actorCellId = h3Grid.cellId(
                lat = actorLocation.lat,
                lon = actorLocation.lon,
                resolution = BreachBalance.H3_RESOLUTION,
            )
            val scanRadius = ceil(
                BreachBalance.SCAN_RANGE_METERS / h3Grid.averageEdgeLengthMeters(BreachBalance.H3_RESOLUTION),
            )
                .toInt()
                .coerceAtLeast(1)
            val candidateCellIds = h3Grid.gridDisk(actorCellId, scanRadius).distinct()

            val nearest = repository.getForCells(candidateCellIds)
                .asSequence()
                .filter { record ->
                    val state = record.state
                    state?.controlledAt == null && state?.lockdownUntil == null
                }
                .minByOrNull { record -> record.definition.location.distanceTo(actorLocation) }

            nearest?.let { Output.Success(it) } ?: Output.Failure(BreachNodeError.NotFound)
        } catch (exception: Throwable) {
            if (exception is CancellationException) {
                throw exception
            }

            Output.Failure(
                BreachNodeError.Unexpected(
                    operation = "find nearest breach node",
                    cause = exception,
                ),
            )
        }
    }
}
