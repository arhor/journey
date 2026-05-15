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
class CompleteBreachUseCase @Inject constructor(
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

            val controlledAt = clock.instant()
            val updatedAt = controlledAt
            val breachNodeId = record.state?.breachNodeId ?: record.definition.id
            val state = BreachNodeState(
                breachNodeId = breachNodeId,
                h3CellId = record.definition.h3CellId,
                discoveredAt = record.state?.discoveredAt ?: controlledAt,
                controlledAt = controlledAt,
                lockdownUntil = record.state?.lockdownUntil,
                updatedAt = updatedAt,
            )

            if (!repository.markControlled(
                    id = breachNodeId,
                    h3CellId = record.definition.h3CellId,
                    controlledAt = controlledAt,
                    updatedAt = updatedAt,
                )
            ) {
                return Output.Failure(
                    BreachNodeError.Unexpected(
                        operation = "complete breach",
                        cause = IllegalStateException("Could not persist controlled breach node $id."),
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
                    operation = "complete breach",
                    cause = exception,
                ),
            )
        }
    }
}
