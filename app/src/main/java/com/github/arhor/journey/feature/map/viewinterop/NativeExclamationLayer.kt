package com.github.arhor.journey.feature.map.viewinterop

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

internal object NativeExclamationLayer {
    private const val LAYER_ID = "native-exclamation-layer"
    @Volatile
    private var isNativeLibraryLoaded = false

    fun addTo(map: MapLibreMap, style: Style) {
        val context = createContext()
        var customLayer: CustomLayer? = null

        try {
            customLayer = CustomLayer(LAYER_ID, context)
            style.addLayer(customLayer)
            map.triggerRepaint()
        } catch (throwable: Throwable) {
            if (customLayer == null) {
                destroyContext(context)
            }
            throw throwable
        }
    }

    internal fun layerId(): String = LAYER_ID

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
