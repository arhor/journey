package com.github.arhor.journey.feature.map.viewinterop

import android.content.res.AssetManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

internal object NativeModelLayer {
    private val nativeLibraryLoadedGate by lazy {
        System.loadLibrary("custom-map-layers")
    }

    private const val LAYER_ID = "native-model-layer"

    fun addTo(
        map: MapLibreMap,
        style: Style,
        assetManager: AssetManager,
        models: List<NativeMapModelSpec>,
    ) {
        addToWithManagedContext(
            models = models,
            createContext = { specs -> createContext(assetManager, specs) },
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
        models: List<NativeMapModelSpec>,
        createContext: (List<NativeMapModelSpec>) -> Long,
        destroyContext: (Long) -> Unit,
        addLayer: (Long) -> Unit,
        repaint: () -> Unit,
    ) {
        val context = createContext(models)
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

    private fun createContext(
        assetManager: AssetManager,
        models: List<NativeMapModelSpec>,
    ): Long {
        nativeLibraryLoadedGate
        return createContextNative(assetManager, models.toTypedArray())
    }

    private fun destroyContext(context: Long) {
        if (context == 0L) {
            return
        }
        nativeLibraryLoadedGate
        destroyContextNative(context)
    }

    @JvmStatic
    private external fun createContextNative(
        assetManager: AssetManager,
        models: Array<NativeMapModelSpec>,
    ): Long

    @JvmStatic
    private external fun destroyContextNative(context: Long)
}
