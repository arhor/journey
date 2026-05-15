package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.error.BreachNodeError
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoverBreachNodeUseCase @Inject constructor(
    private val repository: BreachNodeRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        id: String,
        actorLocation: GeoPoint,
    ): Output<BreachNodeState, BreachNodeError> {
        return try {
            val record = repository.getById(id) ?: return Output.Failure(BreachNodeError.NotFound)
            val distanceMeters = actorLocation.distanceTo(record.definition.location)
            if (distanceMeters > record.definition.interactionRadiusMeters) {
                return Output.Failure(
                    BreachNodeError.NotInRange(
                        id = id,
                        distanceMeters = distanceMeters,
                        interactionRadiusMeters = record.definition.interactionRadiusMeters,
                    ),
                )
            }

            val discoveredAt = clock.instant()
            val updatedAt = discoveredAt
            val breachNodeId = record.state?.breachNodeId ?: record.definition.id
            val state = BreachNodeState(
                breachNodeId = breachNodeId,
                h3CellId = record.definition.h3CellId,
                discoveredAt = discoveredAt,
                controlledAt = record.state?.controlledAt,
                lockdownUntil = record.state?.lockdownUntil,
                updatedAt = updatedAt,
            )

            if (!repository.upsertDiscovered(
                    id = breachNodeId,
                    h3CellId = record.definition.h3CellId,
                    discoveredAt = discoveredAt,
                    updatedAt = updatedAt,
                )
            ) {
                return Output.Failure(
                    BreachNodeError.Unexpected(
                        operation = "discover breach node",
                        cause = IllegalStateException("Could not persist discovered breach node $id."),
                    ),
                )
            }

            Output.Success(state)
        } catch (exception: Throwable) {
            if (exception is CancellationException) {
                throw exception
            }

            Output.Failure(
                BreachNodeError.Unexpected(
                    operation = "discover breach node",
                    cause = exception,
                ),
            )
        }
    }
}
