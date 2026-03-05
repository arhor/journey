package com.github.arhor.journey.ui.views.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState

@Composable
fun SettingsRoute(
    vm: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionController = HealthConnectClient.getOrCreate(context).permissionController
    val requestPermissions = rememberLauncherForActivityResult(
        contract = permissionController.createRequestPermissionResultContract(),
        onResult = { grantedPermissions ->
            vm.dispatch(SettingsIntent.HandleHealthConnectPermissionResult(grantedPermissions))
        },
    )

    LaunchedEffect(Unit) {
        vm.effects.collect {
            when (it) {
                is SettingsEffect.Error -> snackbarHostState.showSnackbar(it.message)
                is SettingsEffect.Success -> snackbarHostState.showSnackbar(it.message)
                is SettingsEffect.LaunchHealthConnectPermissionRequest -> {
                    vm.dispatch(SettingsIntent.HealthConnectPermissionRequestLaunched)
                    requestPermissions.launch(it.permissions)
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
