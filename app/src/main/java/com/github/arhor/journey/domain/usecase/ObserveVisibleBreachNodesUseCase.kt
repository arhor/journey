package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.toOutputFlow
import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.error.BreachNodeError
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveVisibleBreachNodesUseCase @Inject constructor(
    private val repository: BreachNodeRepository,
) {

    operator fun invoke(bounds: GeoBounds): Flow<Output<List<BreachNode>, BreachNodeError>> =
        repository.observeInBounds(bounds).toOutputFlow(
            onSuccess = { records ->
                records.mapNotNull { record -> record.toVisibleBreachNodeOrNull() }
            },
            onFailure = { throwable ->
                BreachNodeError.Unexpected(
                    operation = "observe visible breach nodes",
                    cause = throwable,
                )
            },
        )

    private fun BreachNodeRecord.toVisibleBreachNodeOrNull(): BreachNode? {
        val phase = phaseFor(state)
        if (phase == BreachNodePhase.UNDETECTED) {
            return null
        }

        return BreachNode(
            definition = definition,
            state = state,
            phase = phase,
            distanceMeters = null,
            canDiscover = false,
            canStartUpload = phase == BreachNodePhase.DISCOVERED,
        )
    }

    private fun phaseFor(state: BreachNodeState?): BreachNodePhase =
        when {
            state?.lockdownUntil != null -> BreachNodePhase.LOCKDOWN
            state?.controlledAt != null -> BreachNodePhase.CONTROLLED
            state?.discoveredAt != null -> BreachNodePhase.DISCOVERED
            else -> BreachNodePhase.UNDETECTED
        }
}
