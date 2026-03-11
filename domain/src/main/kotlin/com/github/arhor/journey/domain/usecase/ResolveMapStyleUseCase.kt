package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.map.model.MapResolvedStyle
import com.github.arhor.journey.domain.repository.MapStyleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveMapStyleUseCase @Inject constructor(
    private val repository: MapStyleRepository,
) {
    operator fun invoke(): Flow<MapResolvedStyle> = repository.observeSelectedResolvedStyle()
}
