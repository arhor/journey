package com.github.arhor.journey.feature.map.viewinterop

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

internal object NativeExclamationLayer {
    private const val LAYER_ID = "native-exclamation-layer"
    @Volatile
    private var isNativeLibraryLoaded = false

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
        ensureNativeLibraryLoaded()
        return nativeCreateContext()
    }

    private fun destroyContext(context: Long) {
        if (context == 0L) {
            return
        }
        ensureNativeLibraryLoaded()
        nativeDestroyContext(context)
    }

    private fun ensureNativeLibraryLoaded() {
        if (isNativeLibraryLoaded) {
            return
        }

        synchronized(this) {
            if (isNativeLibraryLoaded) {
                return
            }

            System.loadLibrary("custom-map-layers")
            isNativeLibraryLoaded = true
        }
    }

    @JvmStatic
    private external fun nativeCreateContext(): Long

    @JvmStatic
    private external fun nativeDestroyContext(context: Long)
}
