package com.github.arhor.journey.feature.map.viewinterop

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer
import org.maplibre.android.style.layers.SymbolLayer

private const val JOURNEY_CUSTOM_LAYER_ID = "journey-custom-layer"

internal object NativeMapLibreCustomLayerFactory {
    init {
        System.loadLibrary("journey_map_custom_layer")
    }

    private external fun nativeCreateCustomLayerHost(): Long

    fun create(layerId: String): CustomLayer {
        return CustomLayer(layerId, nativeCreateCustomLayerHost())
    }
}

internal class NativeCustomLayerController {
    private var style: Style? = null

    fun attach(map: MapLibreMap, style: Style) {
        this.style = style

        if (style.getLayer(JOURNEY_CUSTOM_LAYER_ID) != null) {
            return
        }

        val customLayer = NativeMapLibreCustomLayerFactory.create(JOURNEY_CUSTOM_LAYER_ID)
        val firstLabelLayerId = style.getLayers().firstOrNull { it is SymbolLayer }?.id

        if (firstLabelLayerId != null) {
            style.addLayerBelow(customLayer, firstLabelLayerId)
        } else {
            style.addLayer(customLayer)
        }

        map.triggerRepaint()
    }

    fun cleanup() {
        style?.removeLayer(JOURNEY_CUSTOM_LAYER_ID)
        style = null
    }
}
