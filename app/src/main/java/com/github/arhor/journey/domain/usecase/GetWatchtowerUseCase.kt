package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.internal.toWatchtower
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetWatchtowerUseCase @Inject constructor(
    private val repository: WatchtowerRepository,
) {
    suspend operator fun invoke(id: String): Output<Watchtower, UseCaseError> = try {
        repository.getById(id)?.toWatchtower()?.let { Output.Success(it) }
            ?: useCaseNotFound(
                subject = "Watchtower",
                identifier = id,
            )
    } catch (exception: Throwable) {
        if (exception is CancellationException) {
            throw exception
        }

        Output.Failure(
            UseCaseError.Unexpected(
                operation = "get watchtower",
                cause = exception,
            ),
        )
    }
}
