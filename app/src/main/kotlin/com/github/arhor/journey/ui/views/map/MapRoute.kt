package com.github.arhor.journey.ui.views.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    var isLocationPermissionGranted by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    var recenterRequestToken by remember { mutableIntStateOf(0) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isLocationPermissionGranted = context.hasLocationPermission()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        isLocationPermissionGranted = permissions.hasGrantedLocationPermission() ||
            context.hasLocationPermission()
        vm.dispatch(MapIntent.LocationPermissionResult(isGranted = isLocationPermissionGranted))
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is MapEffect.RequestLocationPermission -> {
                    isLocationPermissionGranted = context.hasLocationPermission()

                    if (isLocationPermissionGranted) {
                        vm.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
                    } else {
                        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                }

                is MapEffect.RecenterOnCurrentLocation -> {
                    recenterRequestToken += 1
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
        recenterRequestToken = recenterRequestToken,
    )
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Map<String, Boolean>.hasGrantedLocationPermission(): Boolean =
    entries.any { (permission, isGranted) ->
        isGranted && permission in LOCATION_PERMISSIONS
    }
