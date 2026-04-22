package com.github.arhor.journey.feature.map.viewinterop

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.R
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

const val DEFAULT_VIEW_MAP_STYLE_URL: String = "https://tiles.openfreemap.org/styles/liberty"

private const val DEFAULT_LOCATION_ZOOM = 18.0
private const val LOCATION_FIX_TIMEOUT_MILLIS = 5_000L
private const val LOCATION_UPDATE_INTERVAL_MILLIS = 1_000L
private const val LOCATION_UPDATE_FASTEST_INTERVAL_MILLIS = 500L

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun MapLibreViewMapScreen(
    modifier: Modifier = Modifier,
    styleUrl: String = DEFAULT_VIEW_MAP_STYLE_URL,
    fogOfWar: FogOfWarRenderState = FogOfWarRenderState(),
    onViewportChanged: (GeoBounds) -> Unit = {},
    onLocationPermissionGranted: () -> Unit = {},
    onMapLoadFailed: (String?) -> Unit = {},
) {
    LocationPermissionGate(
        onLocationPermissionGranted = onLocationPermissionGranted,
    ) {
        LegacyMapLibreMap(
            modifier = modifier,
            styleUrl = styleUrl,
            fogOfWar = fogOfWar,
            onViewportChanged = onViewportChanged,
            onMapLoadFailed = onMapLoadFailed,
        )
    }
}

@Composable
private fun LocationPermissionGate(
    onLocationPermissionGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnLocationPermissionGranted by rememberUpdatedState(onLocationPermissionGranted)
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var isPermissionRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var permissionStatus by remember {
        mutableStateOf(context.currentLocationPermissionStatus(activity, hasRequestedPermission))
    }

    val requestLocationPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasRequestedPermission = true
        isPermissionRequestInFlight = false
        permissionStatus = if (grants.values.any { it } || context.hasLocationPermission()) {
            currentOnLocationPermissionGranted()
            LocationPermissionStatus.Granted
        } else {
            context.currentLocationPermissionStatus(activity, hasRequestedPermission = true)
        }
    }

    fun refreshPermissionStatus() {
        val previousStatus = permissionStatus
        val nextStatus = context.currentLocationPermissionStatus(activity, hasRequestedPermission)
        permissionStatus = nextStatus
        if (previousStatus != LocationPermissionStatus.Granted && nextStatus == LocationPermissionStatus.Granted) {
            currentOnLocationPermissionGranted()
        }
    }

    LaunchedEffect(activity) {
        if (context.hasLocationPermission()) {
            permissionStatus = LocationPermissionStatus.Granted
        } else if (activity != null && !hasRequestedPermission && !isPermissionRequestInFlight) {
            permissionStatus = LocationPermissionStatus.Requesting
            isPermissionRequestInFlight = true
            requestLocationPermissions.launch(LOCATION_PERMISSIONS)
        } else {
            refreshPermissionStatus()
        }
    }

    DisposableEffect(lifecycle, activity, hasRequestedPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStatus()
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    when (permissionStatus) {
        LocationPermissionStatus.Granted -> content()
        LocationPermissionStatus.Requesting -> Box(modifier = Modifier.fillMaxSize())
        LocationPermissionStatus.Denied -> LocationPermissionDeniedScreen(
            showSettings = false,
            onRetry = {
                permissionStatus = LocationPermissionStatus.Requesting
                isPermissionRequestInFlight = true
                requestLocationPermissions.launch(LOCATION_PERMISSIONS)
            },
            onOpenSettings = context::openAppSettings,
        )

        LocationPermissionStatus.PermanentlyDenied -> LocationPermissionDeniedScreen(
            showSettings = true,
            onRetry = {
                permissionStatus = LocationPermissionStatus.Requesting
                isPermissionRequestInFlight = true
                requestLocationPermissions.launch(LOCATION_PERMISSIONS)
            },
            onOpenSettings = context::openAppSettings,
        )
    }
}

@Composable
private fun LocationPermissionDeniedScreen(
    showSettings: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.map_view_location_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.widthIn(max = 360.dp),
            text = stringResource(R.string.map_view_location_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        if (showSettings) {
            Button(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.map_view_location_permission_settings))
            }
            OutlinedButton(onClick = onRetry) {
                Text(text = stringResource(R.string.map_view_location_permission_retry))
            }
        } else {
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.map_view_location_permission_retry))
            }
        }
    }
}

@Composable
private fun LegacyMapLibreMap(
    modifier: Modifier = Modifier,
    styleUrl: String,
    fogOfWar: FogOfWarRenderState,
    onViewportChanged: (GeoBounds) -> Unit,
    onMapLoadFailed: (String?) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val mapViewState = rememberSaveable { Bundle() }
    val mapViewHandles = remember { mutableStateMapOf<MapView, MapViewHandle>() }
    var isWaitingForLocation by remember { mutableStateOf(true) }

    LaunchedEffect(styleUrl) {
        isWaitingForLocation = true
        delay(LOCATION_FIX_TIMEOUT_MILLIS)
        isWaitingForLocation = false
    }

    Box(modifier = modifier) {
        key(styleUrl) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapLibre.getInstance(context)

                    val fogLayerController = NativeFogOfWarLayerController()
                    val viewportReporter = NativeMapViewportReporter(
                        onViewportChanged = { bounds ->
                            currentOnViewportChanged(bounds)
                        },
                    )
                    val startupController = MapLocationStartupController(
                        onFirstLocationFix = {
                            isWaitingForLocation = false
                            viewportReporter.reportCurrentViewport()
                        },
                    )
                    val loadFailureListener = MapView.OnDidFailLoadingMapListener { errorMessage ->
                        onMapLoadFailed(errorMessage)
                    }

                    MapView(context).also { mapView ->
                        val observer = MapViewLifecycleObserver(mapView, mapViewState)
                        mapViewHandles[mapView] = MapViewHandle(
                            lifecycleObserver = observer,
                            startupController = startupController,
                            fogLayerController = fogLayerController,
                            viewportReporter = viewportReporter,
                            loadFailureListener = loadFailureListener,
                        )
                        lifecycle.addObserver(observer)
                        mapView.addOnDidFailLoadingMapListener(loadFailureListener)
                        mapView.configureLocationAwareMap(
                            styleUrl = styleUrl,
                            fogOfWar = fogOfWar,
                            startupController = startupController,
                            fogLayerController = fogLayerController,
                            viewportReporter = viewportReporter,
                        )
                    }
                },
                update = { mapView ->
                    mapViewHandles[mapView]?.fogLayerController?.update(fogOfWar)
                },
                onRelease = { mapView ->
                    mapViewHandles.remove(mapView)?.let { handle ->
                        handle.startupController.cleanup()
                        handle.viewportReporter.cleanup()
                        mapView.removeOnDidFailLoadingMapListener(handle.loadFailureListener)
                        lifecycle.removeObserver(handle.lifecycleObserver)
                        handle.lifecycleObserver.save()
                        handle.lifecycleObserver.destroy()
                    }
                },
            )
        }

        if (isWaitingForLocation) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.map_view_location_loading_message),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun MapView.configureLocationAwareMap(
    styleUrl: String,
    fogOfWar: FogOfWarRenderState,
    startupController: MapLocationStartupController,
    fogLayerController: NativeFogOfWarLayerController,
    viewportReporter: NativeMapViewportReporter,
) {
    getMapAsync { map ->
        if (startupController.isReleased) return@getMapAsync

        map.setStyleDefinition(styleUrl) { style ->
            if (startupController.isReleased) return@setStyleDefinition

            fogLayerController.attach(style)
            fogLayerController.update(fogOfWar)
            viewportReporter.attach(map)

            val locationEngine = ForwardingLocationEngine(context)
            val locationRequest = context.locationEngineRequest()
            val locationComponent = map.locationComponent
            val activationOptions = LocationComponentActivationOptions.builder(context, style)
                .locationEngine(locationEngine)
                .locationEngineRequest(locationRequest)
                .build()

            locationComponent.activateLocationComponent(activationOptions)
            locationComponent.setLocationComponentEnabled(true)
            locationComponent.setRenderMode(RenderMode.NORMAL)
            locationComponent.setCameraMode(
                CameraMode.TRACKING,
                0L,
                DEFAULT_LOCATION_ZOOM,
                null,
                null,
                null,
            )

            startupController.start(map, locationEngine, locationRequest)
        }
    }
}

private fun MapLibreMap.setStyleDefinition(
    style: String,
    onStyleLoaded: Style.OnStyleLoaded,
) {
    val builder = if (style.trimStart().startsWith("{")) {
        Style.Builder().fromJson(style)
    } else {
        Style.Builder().fromUri(style)
    }

    setStyle(builder, onStyleLoaded)
}

private data class MapViewHandle(
    val lifecycleObserver: MapViewLifecycleObserver,
    val startupController: MapLocationStartupController,
    val fogLayerController: NativeFogOfWarLayerController,
    val viewportReporter: NativeMapViewportReporter,
    val loadFailureListener: MapView.OnDidFailLoadingMapListener,
)

private class MapLocationStartupController(
    private val onFirstLocationFix: () -> Unit,
) {
    var isReleased = false
        private set

    private var map: MapLibreMap? = null
    private var locationEngine: LocationEngine? = null
    private var locationRequest: LocationEngineRequest? = null
    private var hasFirstFix = false
    private var isRequestingLiveUpdates = false

    private val firstFixCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            val location = result.lastLocation
            if (location != null) {
                handleFirstFix(location)
            } else {
                requestLiveUpdates()
            }
        }

        override fun onFailure(exception: Exception) {
            requestLiveUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    fun start(
        map: MapLibreMap,
        locationEngine: LocationEngine,
        locationRequest: LocationEngineRequest,
    ) {
        if (isReleased) return

        this.map = map
        this.locationEngine = locationEngine
        this.locationRequest = locationRequest

        try {
            locationEngine.getLastLocation(firstFixCallback)
        } catch (_: SecurityException) {
            cleanup()
        }
    }

    fun cleanup() {
        isReleased = true
        removeTemporaryCallback()
        map = null
        locationEngine = null
        locationRequest = null
    }

    @SuppressLint("MissingPermission")
    private fun requestLiveUpdates() {
        if (isReleased || hasFirstFix || isRequestingLiveUpdates) return

        val request = locationRequest ?: return

        try {
            isRequestingLiveUpdates = true
            locationEngine?.requestLocationUpdates(
                request,
                firstFixCallback,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            isRequestingLiveUpdates = false
            cleanup()
        }
    }

    private fun handleFirstFix(location: Location) {
        if (isReleased || hasFirstFix) return

        hasFirstFix = true
        removeTemporaryCallback()
        map?.moveToUserLocation(location)
        onFirstLocationFix()
    }

    private fun removeTemporaryCallback() {
        locationEngine?.removeLocationUpdates(firstFixCallback)
        isRequestingLiveUpdates = false
    }
}

private fun MapLibreMap.moveToUserLocation(location: Location) {
    locationComponent.forceLocationUpdate(location)
    locationComponent.setCameraMode(CameraMode.NONE)
    locationComponent.setCameraMode(
        CameraMode.TRACKING,
        0L,
        DEFAULT_LOCATION_ZOOM,
        null,
        null,
        null,
    )
}

private fun Context.locationEngineRequest(): LocationEngineRequest {
    val hasPreciseLocationPermission = checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val priority = if (hasPreciseLocationPermission) {
        LocationEngineRequest.PRIORITY_HIGH_ACCURACY
    } else {
        LocationEngineRequest.PRIORITY_LOW_POWER
    }

    return LocationEngineRequest.Builder(LOCATION_UPDATE_INTERVAL_MILLIS)
        .setFastestInterval(LOCATION_UPDATE_FASTEST_INTERVAL_MILLIS)
        .setPriority(priority)
        .build()
}

private enum class LocationPermissionStatus {
    Requesting,
    Granted,
    Denied,
    PermanentlyDenied,
}

private fun Context.hasLocationPermission(): Boolean {
    return LOCATION_PERMISSIONS.any { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Context.currentLocationPermissionStatus(
    activity: Activity?,
    hasRequestedPermission: Boolean,
): LocationPermissionStatus {
    return when {
        hasLocationPermission() -> LocationPermissionStatus.Granted
        !hasRequestedPermission -> LocationPermissionStatus.Requesting
        activity != null && LOCATION_PERMISSIONS.none(activity::shouldShowRequestPermissionRationale) -> {
            LocationPermissionStatus.PermanentlyDenied
        }

        else -> LocationPermissionStatus.Denied
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    startActivity(intent)
}

private class MapViewLifecycleObserver(
    private val mapView: MapView,
    private val savedState: Bundle,
) : LifecycleEventObserver {

    private var isDestroyed = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> mapView.onCreate(savedState)
            Lifecycle.Event.ON_START -> mapView.onStart()
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            Lifecycle.Event.ON_STOP -> {
                save()
                mapView.onStop()
            }

            Lifecycle.Event.ON_DESTROY -> destroy()
            Lifecycle.Event.ON_ANY -> Unit
        }
    }

    fun save() {
        mapView.onSaveInstanceState(savedState)
    }

    fun destroy() {
        if (!isDestroyed) {
            mapView.onDestroy()
            isDestroyed = true
        }
    }
}
