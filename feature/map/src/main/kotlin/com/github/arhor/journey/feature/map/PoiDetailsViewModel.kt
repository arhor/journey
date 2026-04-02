package com.github.arhor.journey.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.domain.usecase.GetPointOfInterestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PoiDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPointOfInterest: GetPointOfInterestUseCase,
) : ViewModel() {
    private val destination = savedStateHandle.toRoute<PoiDetailsDestination>()

    val uiState = flow {
        val poiId = destination.poiId.toLongOrNull()
        if (poiId == null) {
            emit(
                PoiDetailsUiState.Failure(
                    errorMessage = POI_NOT_FOUND_MESSAGE,
                ),
            )
            return@flow
        }

        when (val result = getPointOfInterest(poiId)) {
            is Output.Success -> {
                val poi = result.value
                emit(
                    PoiDetailsUiState.Content(
                        id = poi.id.toString(),
                        name = poi.name,
                        description = poi.description,
                        category = poi.category.name.replace('_', ' '),
                        latitude = poi.location.lat,
                        longitude = poi.location.lon,
                        radiusMeters = poi.radiusMeters,
                    ),
                )
            }

            is Output.Failure -> {
                emit(
                    PoiDetailsUiState.Failure(
                        errorMessage = result.error.resolveMessage(POI_NOT_FOUND_MESSAGE),
                    ),
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PoiDetailsUiState.Loading,
    )

    private companion object {
        const val POI_NOT_FOUND_MESSAGE = "Point of interest not found."
    }
}
