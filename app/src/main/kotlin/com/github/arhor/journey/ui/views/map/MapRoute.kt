package com.github.arhor.journey.ui.views.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState
import com.github.arhor.journey.ui.views.map.model.LatLng
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val context = LocalContext.current
    val state = vm.uiState.collectAsStateWithLifecycle().value

    val latestHasLocationPermission = rememberUpdatedState(context.hasLocationPermission())

    @SuppressLint("MissingPermission")
    val locationProvider = if (latestHasLocationPermission.value) rememberDefaultLocationProvider() else rememberNullLocationProvider()
    val userLocationState = rememberUserLocationState(locationProvider)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        vm.dispatch(
            MapIntent.RecenterPermissionResolved(
                isGranted = isGranted || context.hasLocationPermission(),
                location = userLocationState.location?.toLatLng(),
            ),
        )
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is MapEffect.RequestLocationPermission -> {
                    if (context.hasLocationPermission()) {
                        vm.dispatch(
                            MapIntent.RecenterPermissionResolved(
                                isGranted = true,
                                location = userLocationState.location?.toLatLng(),
                            ),
                        )
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                is MapEffect.OpenObjectDetails -> {
                    snackbarHostState.showSnackbar("Open details for ${effect.objectId}")
                }
            }
        }
    }

    MapScreen(
        state = state,
        userLocationState = if (latestHasLocationPermission.value) userLocationState else null,
        dispatch = vm::dispatch,
    )
}

private fun android.content.Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}

private fun org.maplibre.compose.location.Location.toLatLng(): LatLng {
    return LatLng(
        latitude = position.latitude,
        longitude = position.longitude,
    )
}
