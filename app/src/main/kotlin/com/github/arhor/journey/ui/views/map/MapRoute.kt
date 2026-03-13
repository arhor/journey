package com.github.arhor.journey.ui.views.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            vm.dispatch(
                MapIntent.LocationPermissionResult(it.hasGrantedLocationPermission() || context.hasLocationPermission())
            )
        },
    )

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is MapEffect.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                }

                is MapEffect.OpenObjectDetails -> {
                    snackbarHostState.showSnackbar("Open details for ${effect.objectId}")
                }
            }
        }
    }

    MapScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Map<String, Boolean>.hasGrantedLocationPermission(): Boolean {
    return LOCATION_PERMISSIONS.any { this[it] == true }
}
