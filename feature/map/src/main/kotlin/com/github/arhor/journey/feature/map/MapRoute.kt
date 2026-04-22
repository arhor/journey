package com.github.arhor.journey.feature.map

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
import com.github.arhor.journey.feature.map.viewinterop.MapLibreViewMapScreen

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    hudVm: MapHudViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val hudState by hudVm.uiState.collectAsStateWithLifecycle()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            vm.dispatch(
                MapIntent.LocationPermissionResult(
                    isGranted = grants.any { it.value } || context.hasLocationPermission(),
                ),
            )
        },
    )

    LaunchedEffect(Unit) {
        vm.dispatch(MapIntent.MapOpened)
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                MapEffect.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                }
            }
        }
    }

    // TODO: final goal is to completely replace maplibre-compose library with native view
//    MapScreen(
//        state = state,
//        hudState = hudState,
//        dispatch = vm::dispatch,
//        onOpenHero = onOpenHero,
//        onOpenSettings = onOpenSettings,
//    )

    MapLibreViewMapScreen()
}

private fun Context.hasLocationPermission(): Boolean {
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
