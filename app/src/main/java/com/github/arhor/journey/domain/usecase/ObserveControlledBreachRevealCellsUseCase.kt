package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.toOutputFlow
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.error.BreachNodeError
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveControlledBreachRevealCellsUseCase @Inject constructor(
    private val repository: BreachNodeRepository,
) {

    operator fun invoke(bounds: GeoBounds): Flow<Output<Set<String>, BreachNodeError>> =
        repository.observeControlledCells(bounds).toOutputFlow(
            onSuccess = { it },
            onFailure = { throwable ->
                BreachNodeError.Unexpected(
                    operation = "observe controlled breach reveal cells",
                    cause = throwable,
                )
            },
        )
}
