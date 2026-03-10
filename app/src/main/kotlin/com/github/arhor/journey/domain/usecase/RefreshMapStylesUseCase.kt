package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.MapStyleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshMapStylesUseCase @Inject constructor(
    private val repository: MapStyleRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshRemoteStyles()
}
