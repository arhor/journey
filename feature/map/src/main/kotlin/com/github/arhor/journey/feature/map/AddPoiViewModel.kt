package com.github.arhor.journey.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.usecase.AddPointOfInterestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AddPoiViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addPointOfInterest: AddPointOfInterestUseCase,
) : ViewModel() {
    private val destination = savedStateHandle.toRoute<AddPoiDestination>()

    private val _uiState = MutableStateFlow(
        AddPoiUiState(
            latitude = destination.initialLatitude.formatCoordinate(),
            longitude = destination.initialLongitude.formatCoordinate(),
        ),
    )
    private val _effects = MutableSharedFlow<AddPoiEffect>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    val uiState: StateFlow<AddPoiUiState> = _uiState.asStateFlow()
    val effects = _effects.asSharedFlow()

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onCategorySelected(value: PoiCategory) {
        _uiState.update { it.copy(selectedCategory = value) }
    }

    fun onRadiusChanged(value: String) {
        _uiState.update { it.copy(radiusMeters = value) }
    }

    fun onLatitudeChanged(value: String) {
        _uiState.update { it.copy(latitude = value) }
    }

    fun onLongitudeChanged(value: String) {
        _uiState.update { it.copy(longitude = value) }
    }

    fun save() {
        val currentState = _uiState.value
        if (currentState.isSaving) {
            return
        }

        val name = currentState.name.trim()
        if (name.isBlank()) {
            emitEffect(AddPoiEffect.ShowMessage(NAME_REQUIRED_MESSAGE))
            return
        }

        val radiusMeters = currentState.radiusMeters.toIntOrNull()
        if (radiusMeters == null || radiusMeters <= 0) {
            emitEffect(AddPoiEffect.ShowMessage(RADIUS_INVALID_MESSAGE))
            return
        }

        val latitude = currentState.latitude.toNormalizedDoubleOrNull()
        if (latitude == null || latitude !in -90.0..90.0) {
            emitEffect(AddPoiEffect.ShowMessage(LATITUDE_INVALID_MESSAGE))
            return
        }

        val longitude = currentState.longitude.toNormalizedDoubleOrNull()
        if (longitude == null || longitude !in -180.0..180.0) {
            emitEffect(AddPoiEffect.ShowMessage(LONGITUDE_INVALID_MESSAGE))
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            when (
                val result = addPointOfInterest(
                    name = name,
                    description = currentState.description.trim().ifBlank { null },
                    category = currentState.selectedCategory,
                    location = GeoPoint(lat = latitude, lon = longitude),
                    radiusMeters = radiusMeters,
                )
            ) {
                is Output.Success -> {
                    emitEffect(AddPoiEffect.ShowMessage(ADD_POI_SUCCESS_MESSAGE))
                    emitEffect(AddPoiEffect.Saved)
                }

                is Output.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    emitEffect(
                        AddPoiEffect.ShowMessage(
                            result.error.resolveMessage(ADD_POI_FAILED_MESSAGE),
                        ),
                    )
                }
            }
        }
    }

    private fun emitEffect(effect: AddPoiEffect) {
        if (!_effects.tryEmit(effect)) {
            viewModelScope.launch {
                _effects.emit(effect)
            }
        }
    }

    private companion object {
        const val NAME_REQUIRED_MESSAGE = "Name is required."
        const val RADIUS_INVALID_MESSAGE = "Radius must be a positive whole number."
        const val LATITUDE_INVALID_MESSAGE = "Latitude must be between -90 and 90."
        const val LONGITUDE_INVALID_MESSAGE = "Longitude must be between -180 and 180."
        const val ADD_POI_SUCCESS_MESSAGE = "Point of interest added."
        const val ADD_POI_FAILED_MESSAGE = "Failed to add point of interest."
    }
}

private fun Double.formatCoordinate(): String = String.format(Locale.US, "%.6f", this)

private fun String.toNormalizedDoubleOrNull(): Double? = replace(',', '.').toDoubleOrNull()
