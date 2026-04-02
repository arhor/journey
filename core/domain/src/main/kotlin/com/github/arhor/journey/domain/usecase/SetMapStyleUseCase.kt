package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mapStylesRepository: MapStylesRepository,
) {
    suspend operator fun invoke(mapStyleId: String): Output<Unit, UseCaseError> {
        val mapStyles = when (val result = mapStylesRepository.observeMapStyles().value) {
            is Output.Success -> result.value
            is Output.Failure -> {
                return Output.Failure(
                    UseCaseError.Unexpected(
                        operation = "set map style",
                        cause = result.error.asThrowable("Failed to load map styles."),
                    ),
                )
            }
        }
        val mapStyle = mapStyles.find { it.id == mapStyleId }
            ?: return useCaseNotFound(
                subject = "Map style",
                identifier = mapStyleId,
            )

        return runSuspendingUseCaseCatching("set map style") {
            settingsRepository.setSelectedMapStyleId(mapStyle.id)
        }
    }
}
