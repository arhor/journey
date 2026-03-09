package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.MapResolvedStyle
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStyleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveMapStyleUseCase @Inject constructor(
    private val repository: MapStyleRepository,
) {
    operator fun invoke(style: MapStyle): MapResolvedStyle = repository.resolve(style)
}
