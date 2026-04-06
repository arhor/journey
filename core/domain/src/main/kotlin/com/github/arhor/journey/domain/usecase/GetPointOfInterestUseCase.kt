package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPointOfInterestUseCase @Inject constructor(
    private val repository: PointOfInterestRepository,
) {
    suspend operator fun invoke(id: Long): Output<PointOfInterest, UseCaseError> = try {
        repository.getById(id)?.let { Output.Success(it) }
            ?: useCaseNotFound(
                subject = "Point of interest",
                identifier = id,
            )
    } catch (exception: Throwable) {
        if (exception is CancellationException) {
            throw exception
        }

        Output.Failure(
            UseCaseError.Unexpected(
                operation = "get point of interest",
                cause = exception,
            ),
        )
    }
}
