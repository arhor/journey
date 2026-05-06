package com.github.arhor.journey.feature.map.viewinterop

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

internal object NativeExclamationLayer {
    init {
        System.loadLibrary("custom-map-layers")
    }

    private const val LAYER_ID = "native-exclamation-layer"

    fun addTo(map: MapLibreMap, style: Style) {
        addToWithManagedContext(
            createContext = ::createContext,
            destroyContext = ::destroyContext,
            addLayer = { context ->
                val customLayer = CustomLayer(LAYER_ID, context)
                style.addLayer(customLayer)
            },
            repaint = map::triggerRepaint,
        )
    }

    internal fun layerId(): String = LAYER_ID

    internal fun addToWithManagedContext(
        createContext: () -> Long,
        destroyContext: (Long) -> Unit,
        addLayer: (Long) -> Unit,
        repaint: () -> Unit,
    ) {
        val context = createContext()
        var layerAdded = false

        try {
            addLayer(context)
            layerAdded = true
            repaint()
        } catch (throwable: Throwable) {
            if (!layerAdded) {
                destroyContext(context)
            }
            throw throwable
        }
    }

    private fun createContext(): Long {
        return nativeCreateContext()
    }

    private fun destroyContext(context: Long) {
        if (context == 0L) {
            return
        }
        nativeDestroyContext(context)
    }

    @JvmStatic
    private external fun nativeCreateContext(): Long

    @JvmStatic
    private external fun nativeDestroyContext(context: Long)
}
