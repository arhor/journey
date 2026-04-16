package com.github.arhor.journey.feature.map.location

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.github.arhor.journey.feature.map.CurrentLocationUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationProvider
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

internal const val USER_LOCATION_PUCK_ID_PREFIX = "user-location"

@Composable
internal fun rememberMapLocationProvider(
    currentLocation: CurrentLocationUiModel?,
): MapUiLocationProvider {
    val provider = remember {
        MapUiLocationProvider(currentLocation?.toMapLibreLocation())
    }

    LaunchedEffect(currentLocation, provider) {
        provider.update(currentLocation?.toMapLibreLocation())
    }

    return provider
}

internal class MapUiLocationProvider(
    initialLocation: Location?,
) : LocationProvider {
    private val locations = MutableStateFlow(initialLocation)

    override val location: StateFlow<Location?> = locations

    fun update(location: Location?) {
        locations.value = location
    }
}

internal fun CurrentLocationUiModel.toMapLibreLocation(
    nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
): Location =
    Location(
        position = Position(
            latitude = position.latitude,
            longitude = position.longitude,
        ),
        accuracy = horizontalAccuracyMeters ?: 0.0,
        bearing = bearingDegrees,
        bearingAccuracy = bearingAccuracyDegrees,
        speed = speedMetersPerSecond,
        speedAccuracy = null,
        timestamp = elapsedRealtimeNanos.toTimeMark(nowElapsedRealtimeNanos),
    )

internal fun Long?.toTimeMark(
    nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) = this
    ?.let { elapsedRealtimeNanos ->
        val age = (nowElapsedRealtimeNanos() - elapsedRealtimeNanos)
            .coerceAtLeast(0L)
            .nanoseconds
        TimeSource.Monotonic.markNow() - age
    }
    ?: TimeSource.Monotonic.markNow()
