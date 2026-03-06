package com.github.arhor.journey.ui.views.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.arhor.journey.ui.LocalSnackbarHostState

@Composable
fun SettingsRoute(
    vm: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPermissions = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { grantedPermissions ->
            vm.dispatch(SettingsIntent.HandleHealthConnectPermissionResult(grantedPermissions))
        },
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.dispatch(SettingsIntent.RefreshHealthConnectStatus)
    }

    LaunchedEffect(Unit) {
        vm.effects.collect {
            when (it) {
                is SettingsEffect.Error -> snackbarHostState.showSnackbar(it.message)
                SettingsEffect.OpenHealthConnectManagement -> {
                    val healthConnectIntent = HealthConnectClient.getHealthConnectManageDataIntent(context)
                    runCatching { context.startActivity(healthConnectIntent) }
                        .onFailure { error ->
                            val message = if (error is ActivityNotFoundException) {
                                "Failed to open Health Connect settings."
                            } else {
                                error.message ?: "Failed to open Health Connect settings."
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                }
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
