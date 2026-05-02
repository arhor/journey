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
import android.view.MotionEvent
import android.view.View
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
import com.github.arhor.journey.R
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.BEARING_DEGREES_PER_PIXEL
import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_MAX_TILT
import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_MIN_TILT
import com.github.arhor.journey.feature.map.TILT_DEGREES_PER_PIXEL
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
import com.github.arhor.journey.feature.map.gesture.PlayerCenteredCameraGestureTracker
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng as FeatureLatLng
import com.github.arhor.journey.feature.map.gesture.normalizeBearing
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
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
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_VIEW_MAP_STYLE_ASSET_URI: String = "asset://map/styles/cyberpunk.json"

private const val DEFAULT_LOCATION_ZOOM = 18.0
private const val STARTUP_GATE_TIMEOUT_MILLIS = 5_000L
private const val LOCATION_UPDATE_INTERVAL_MILLIS = 1_000L
private const val LOCATION_UPDATE_FASTEST_INTERVAL_MILLIS = 500L

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun MapLibreViewMapScreen(
    modifier: Modifier = Modifier,
    styleUri: String = DEFAULT_VIEW_MAP_STYLE_ASSET_URI,
    fogOfWar: FogOfWarRenderState = FogOfWarRenderState(),
    onViewportChanged: (GeoBounds) -> Unit = {},
    onCameraGestureStarted: (CameraPositionState) -> Unit = {},
    onCameraSettled: (CameraPositionState, CameraUpdateOrigin) -> Unit = { _, _ -> },
    onLocationPermissionGranted: () -> Unit = {},
    onMapLoadFailed: (String?) -> Unit = {},
    onMapSurfaceSessionStarted: (Long) -> Unit = {},
    onFirstLocationFix: (Long) -> Unit = {},
    onFirstMapFrameRendered: (Long) -> Unit = {},
    onStartupTimeout: (Long) -> Unit = {},
) {
    LocationPermissionGate(
        onLocationPermissionGranted = onLocationPermissionGranted,
    ) {
        LegacyMapLibreMap(
            modifier = modifier,
            styleUri = styleUri,
            fogOfWar = fogOfWar,
            onViewportChanged = onViewportChanged,
            onCameraGestureStarted = onCameraGestureStarted,
            onCameraSettled = onCameraSettled,
            onMapLoadFailed = onMapLoadFailed,
            onMapSurfaceSessionStarted = onMapSurfaceSessionStarted,
            onFirstLocationFix = onFirstLocationFix,
            onFirstMapFrameRendered = onFirstMapFrameRendered,
            onStartupTimeout = onStartupTimeout,
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
    styleUri: String,
    fogOfWar: FogOfWarRenderState,
    onViewportChanged: (GeoBounds) -> Unit,
    onCameraGestureStarted: (CameraPositionState) -> Unit,
    onCameraSettled: (CameraPositionState, CameraUpdateOrigin) -> Unit,
    onMapLoadFailed: (String?) -> Unit,
    onMapSurfaceSessionStarted: (Long) -> Unit,
    onFirstLocationFix: (Long) -> Unit,
    onFirstMapFrameRendered: (Long) -> Unit,
    onStartupTimeout: (Long) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnCameraGestureStarted by rememberUpdatedState(onCameraGestureStarted)
    val currentOnCameraSettled by rememberUpdatedState(onCameraSettled)
    val currentOnMapLoadFailed by rememberUpdatedState(onMapLoadFailed)
    val currentOnMapSurfaceSessionStarted by rememberUpdatedState(onMapSurfaceSessionStarted)
    val currentOnFirstLocationFix by rememberUpdatedState(onFirstLocationFix)
    val currentOnFirstMapFrameRendered by rememberUpdatedState(onFirstMapFrameRendered)
    val currentOnStartupTimeout by rememberUpdatedState(onStartupTimeout)
    val mapViewState = rememberSaveable { Bundle() }
    val mapViewHandles = remember { mutableStateMapOf<MapView, MapViewHandle>() }

    Box(modifier = modifier) {
        key(styleUri) {
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
                    val cameraGestureController = NativeCameraGestureController(
                        onCameraGestureStarted = { position ->
                            currentOnCameraGestureStarted(position)
                        },
                        onCameraSettled = { position, origin ->
                            currentOnCameraSettled(position, origin)
                        },
                    )
                    val sessionId = nextMapSurfaceSessionId()

                    MapView(context).also { mapView ->
                        val startupGateController = MapStartupGateController(
                            sessionId = sessionId,
                            onMapSurfaceSessionStarted = { callbackSessionId ->
                                currentOnMapSurfaceSessionStarted(callbackSessionId)
                            },
                            onFirstLocationFix = { callbackSessionId ->
                                currentOnFirstLocationFix(callbackSessionId)
                            },
                            onFirstMapFrameRendered = { callbackSessionId ->
                                currentOnFirstMapFrameRendered(callbackSessionId)
                            },
                            onStartupTimeout = { callbackSessionId ->
                                currentOnStartupTimeout(callbackSessionId)
                            },
                            scheduleTimeout = { delayMillis, runnable ->
                                mapView.postDelayed(runnable, delayMillis)
                            },
                            cancelTimeout = { runnable ->
                                mapView.removeCallbacks(runnable)
                            },
                        )
                        val startupController = MapLocationStartupController(
                            onLocationUpdated = { location ->
                                cameraGestureController.onLocationUpdated(location)
                            },
                            onFirstLocationFix = {
                                startupGateController.onFirstLocationFixAcquired()
                                viewportReporter.reportCurrentViewport()
                            },
                        )
                        val loadFailureListener = MapView.OnDidFailLoadingMapListener { errorMessage ->
                            currentOnMapLoadFailed(errorMessage)
                        }
                        val observer = MapViewLifecycleObserver(mapView, mapViewState)
                        startupGateController.attachToMapView(
                            timeoutMillis = STARTUP_GATE_TIMEOUT_MILLIS,
                        )
                        val renderReadinessListeners = mapView.attachRenderReadinessListeners(
                            onFirstFrameRendered = startupGateController::onFirstMapFrameRendered,
                        )
                        mapViewHandles[mapView] = MapViewHandle(
                            lifecycleObserver = observer,
                            startupController = startupController,
                            startupGateController = startupGateController,
                            renderReadinessListeners = renderReadinessListeners,
                            fogLayerController = fogLayerController,
                            viewportReporter = viewportReporter,
                            cameraGestureController = cameraGestureController,
                            loadFailureListener = loadFailureListener,
                        )
                        lifecycle.addObserver(observer)
                        mapView.addOnDidFailLoadingMapListener(loadFailureListener)
                        mapView.configureLocationAwareMap(
                            styleUri = styleUri,
                            fogOfWar = fogOfWar,
                            startupController = startupController,
                            fogLayerController = fogLayerController,
                            viewportReporter = viewportReporter,
                            cameraGestureController = cameraGestureController,
                        )
                    }
                },
                update = { mapView ->
                    mapViewHandles[mapView]?.let { handle ->
                        handle.fogLayerController.update(fogOfWar)
                    }
                },
                onRelease = { mapView ->
                    mapViewHandles.remove(mapView)?.let { handle ->
                        handle.startupController.cleanup()
                        handle.startupGateController.cleanup()
                        mapView.detachRenderReadinessListeners(handle.renderReadinessListeners)
                        handle.viewportReporter.cleanup()
                        handle.cameraGestureController.cleanup()
                        mapView.removeOnDidFailLoadingMapListener(handle.loadFailureListener)
                        lifecycle.removeObserver(handle.lifecycleObserver)
                        handle.lifecycleObserver.save()
                        handle.lifecycleObserver.destroy()
                    }
                },
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun MapView.configureLocationAwareMap(
    styleUri: String,
    fogOfWar: FogOfWarRenderState,
    startupController: MapLocationStartupController,
    fogLayerController: NativeFogOfWarLayerController,
    viewportReporter: NativeMapViewportReporter,
    cameraGestureController: NativeCameraGestureController,
) {
    getMapAsync { map ->
        if (startupController.isReleased) return@getMapAsync

        map.configureUiSettings()
        map.setStyleDefinition(styleUri) { style ->
            if (startupController.isReleased) return@setStyleDefinition

            fogLayerController.attach(style)
            fogLayerController.update(fogOfWar)
            viewportReporter.attach(map)

            val locationEngine = ForwardingLocationEngine(context) { location ->
                cameraGestureController.onLocationUpdated(location)
            }
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
            cameraGestureController.attach(
                mapView = this,
                map = map,
            )

            startupController.start(map, locationEngine, locationRequest)
        }
    }
}

private fun MapLibreMap.configureUiSettings() {
    with(uiSettings) {
        isCompassEnabled = false
        isAttributionEnabled = false
        isLogoEnabled = false

        isScrollGesturesEnabled = false
        isHorizontalScrollGesturesEnabled = false
        isRotateGesturesEnabled = false
        isTiltGesturesEnabled = false
        isZoomGesturesEnabled = true
        isDoubleTapGesturesEnabled = true
        isQuickZoomGesturesEnabled = true
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
    val startupGateController: MapStartupGateController,
    val renderReadinessListeners: MapRenderReadinessListeners,
    val fogLayerController: NativeFogOfWarLayerController,
    val viewportReporter: NativeMapViewportReporter,
    val cameraGestureController: NativeCameraGestureController,
    val loadFailureListener: MapView.OnDidFailLoadingMapListener,
)

private data class MapRenderReadinessListeners(
    val frameListener: MapView.OnDidFinishRenderingFrameListener,
)

private val mapSurfaceSessionCounter = AtomicLong(1L)

private fun nextMapSurfaceSessionId(): Long = mapSurfaceSessionCounter.getAndIncrement()

internal class MapStartupGateController(
    private val sessionId: Long,
    private val onMapSurfaceSessionStarted: (Long) -> Unit,
    private val onFirstLocationFix: (Long) -> Unit,
    private val onFirstMapFrameRendered: (Long) -> Unit,
    private val onStartupTimeout: (Long) -> Unit,
    private val scheduleTimeout: (Long, Runnable) -> Unit,
    private val cancelTimeout: (Runnable) -> Unit,
) {
    private var isReleased = false
    private var hasFirstLocationFix = false
    private var hasFirstFrameRendered = false
    private var hasTimedOut = false
    private var timeoutRunnable: Runnable? = null

    init {
        onMapSurfaceSessionStarted(sessionId)
    }

    fun attachToMapView(timeoutMillis: Long) {
        if (isReleased) return

        val runnable = Runnable { onTimeout() }
        timeoutRunnable = runnable
        scheduleTimeout(timeoutMillis, runnable)
    }

    fun cleanup() {
        if (isReleased) return

        isReleased = true
        timeoutRunnable?.let(cancelTimeout)
        timeoutRunnable = null
    }

    fun onFirstLocationFixAcquired() {
        if (isReleased || hasFirstLocationFix) return

        hasFirstLocationFix = true
        onFirstLocationFix(sessionId)
        maybeCancelTimeoutAfterReadiness()
    }

    fun onFirstMapFrameRendered() {
        if (isReleased || hasFirstFrameRendered) return

        hasFirstFrameRendered = true
        onFirstMapFrameRendered(sessionId)
        maybeCancelTimeoutAfterReadiness()
    }

    private fun onTimeout() {
        if (isReleased || hasTimedOut || (hasFirstLocationFix && hasFirstFrameRendered)) return

        hasTimedOut = true
        onStartupTimeout(sessionId)
    }

    private fun maybeCancelTimeoutAfterReadiness() {
        if (hasFirstLocationFix && hasFirstFrameRendered) {
            timeoutRunnable?.let(cancelTimeout)
            timeoutRunnable = null
        }
    }
}

private fun MapView.attachRenderReadinessListeners(
    onFirstFrameRendered: () -> Unit,
): MapRenderReadinessListeners {
    val frameListener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
        onFirstFrameRendered()
    }

    addOnDidFinishRenderingFrameListener(frameListener)

    return MapRenderReadinessListeners(
        frameListener = frameListener,
    )
}

private fun MapView.detachRenderReadinessListeners(listeners: MapRenderReadinessListeners) {
    removeOnDidFinishRenderingFrameListener(listeners.frameListener)
}

private class NativeCameraGestureController(
    private val onCameraGestureStarted: (CameraPositionState) -> Unit,
    private val onCameraSettled: (CameraPositionState, CameraUpdateOrigin) -> Unit,
) {
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var latestUserLocation: LatLng? = null
    private var lastCameraMoveOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC
    private var isCustomCameraGestureActive = false

    private val cameraGestureTracker = PlayerCenteredCameraGestureTracker(
        bearingDegreesPerPixel = BEARING_DEGREES_PER_PIXEL,
        tiltDegreesPerPixel = TILT_DEGREES_PER_PIXEL,
        minTilt = DEFAULT_CAMERA_MIN_TILT,
        maxTilt = DEFAULT_CAMERA_MAX_TILT,
    )

    private val cameraMoveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
        lastCameraMoveOrigin = if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            CameraUpdateOrigin.USER
        } else {
            CameraUpdateOrigin.PROGRAMMATIC
        }

        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && !isCustomCameraGestureActive) {
            map?.readCurrentCameraState()?.let(onCameraGestureStarted)
        }
    }

    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
        if (isCustomCameraGestureActive) {
            return@OnCameraIdleListener
        }

        map?.readCurrentCameraState()?.let { position ->
            onCameraSettled(position, lastCameraMoveOrigin)
        }
    }

    private val touchListener = View.OnTouchListener { _, event ->
        val map = map ?: return@OnTouchListener false
        val currentPosition = map.cameraPosition
        val update = cameraGestureTracker.onMotionEvent(
            action = event.actionMasked,
            x = event.x,
            y = event.y,
            pointerCount = event.pointerCount,
            currentBearing = currentPosition.bearing,
            currentTilt = currentPosition.tilt,
        )

        if (update.didStartInteraction) {
            map.locationComponent.setCameraMode(CameraMode.NONE)
            isCustomCameraGestureActive = true
            map.readCurrentCameraState()?.let(onCameraGestureStarted)
        }

        if (update.bearing != null && update.tilt != null) {
            map.moveCameraToUserCenteredPosition(
                target = latestUserLocation ?: currentPosition.target,
                bearing = update.bearing,
                tilt = update.tilt,
            )
        }

        if (update.didEndInteraction && isCustomCameraGestureActive) {
            isCustomCameraGestureActive = false
            val settledPosition = map.readCurrentCameraState()
            restoreTrackingCameraMode(map)
            settledPosition?.let { position ->
                onCameraSettled(position, CameraUpdateOrigin.USER)
            }
        }

        if (isCustomCameraGestureActive) {
            true
        } else {
            false
        }
    }

    fun attach(
        mapView: MapView,
        map: MapLibreMap,
    ) {
        cleanup()

        this.mapView = mapView
        this.map = map
        mapView.setOnTouchListener(touchListener)
        map.addOnCameraMoveStartedListener(cameraMoveStartedListener)
        map.addOnCameraIdleListener(cameraIdleListener)
    }

    fun cleanup() {
        map?.removeOnCameraMoveStartedListener(cameraMoveStartedListener)
        map?.removeOnCameraIdleListener(cameraIdleListener)

        mapView?.setOnTouchListener(null)
        mapView = null
        map = null
        latestUserLocation = null
        isCustomCameraGestureActive = false
        lastCameraMoveOrigin = CameraUpdateOrigin.PROGRAMMATIC
    }

    fun onLocationUpdated(location: Location) {
        val mapTarget = LatLng(location.latitude, location.longitude)
        latestUserLocation = mapTarget

        val map = map ?: return
        if (isCustomCameraGestureActive || map.locationComponent.cameraMode == CameraMode.NONE) {
            map.moveCameraToUserCenteredPosition(target = mapTarget)
        }
    }

    private fun restoreTrackingCameraMode(map: MapLibreMap) {
        map.locationComponent.setCameraMode(
            CameraMode.TRACKING,
            0L,
            null,
            null,
            null,
            null,
        )
    }
}

private fun MapLibreMap.readCurrentCameraState(): CameraPositionState? {
    val target = cameraPosition.target ?: return null
    return CameraPositionState(
        target = FeatureLatLng(
            latitude = target.latitude,
            longitude = target.longitude,
        ),
        zoom = cameraPosition.zoom,
        bearing = normalizeBearing(cameraPosition.bearing),
        tilt = cameraPosition.tilt.coerceIn(DEFAULT_CAMERA_MIN_TILT, DEFAULT_CAMERA_MAX_TILT),
        centerAltitudeMeters = normalizeCenterAltitudeMeters(cameraPosition.centerAltitude),
    )
}

private fun MapLibreMap.moveCameraToUserCenteredPosition(
    target: LatLng? = cameraPosition.target,
    bearing: Double = cameraPosition.bearing,
    tilt: Double = cameraPosition.tilt,
) {
    val resolvedTarget = target ?: return
    val currentPosition = cameraPosition
    val updatedPosition = CameraPosition.Builder(currentPosition)
        .target(resolvedTarget)
        .bearing(normalizeBearing(bearing))
        .tilt(tilt.coerceIn(DEFAULT_CAMERA_MIN_TILT, DEFAULT_CAMERA_MAX_TILT))
        .zoom(currentPosition.zoom)
        .build()

    moveCamera(CameraUpdateFactory.newCameraPosition(updatedPosition))
}

internal fun normalizeCenterAltitudeMeters(centerAltitudeMeters: Double): Double? {
    return centerAltitudeMeters.takeIf(Double::isFinite)
}

private class MapLocationStartupController(
    private val onLocationUpdated: (Location) -> Unit,
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
        onLocationUpdated(location)
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
