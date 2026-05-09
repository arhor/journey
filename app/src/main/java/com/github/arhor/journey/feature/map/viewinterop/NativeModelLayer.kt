package com.github.arhor.journey.feature.map.viewinterop

import android.content.res.AssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

internal object NativeModelLayer {
    private val nativeLibraryLoadedGate by lazy {
        System.loadLibrary("custom-map-layers")
    }

    private const val LAYER_ID = "native-model-layer"
    private const val DOUBLE_BYTES = java.lang.Double.BYTES
    private const val DOUBLES_PER_RECORD = 5
    private const val RECORD_SIZE_BYTES = DOUBLE_BYTES * DOUBLES_PER_RECORD

    internal data class NativePayload(
        val numericRecords: ByteBuffer,
        val assetPaths: Array<String>,
        val count: Int,
    )

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
        val payload = buildNativePayload(models)
        return createContextNative(assetManager, payload.numericRecords, payload.assetPaths, payload.count)
    }

    internal fun buildNativePayload(models: List<NativeMapModelSpec>): NativePayload {
        val bufferSizeBytes = Math.multiplyExact(models.size, RECORD_SIZE_BYTES)
        val buffer = ByteBuffer
            .allocateDirect(bufferSizeBytes)
            .order(ByteOrder.nativeOrder())
        val assetPaths = Array(models.size) { index ->
            val model = models[index]

            buffer.putDouble(model.latitude)
            buffer.putDouble(model.longitude)
            buffer.putDouble(model.altitudeMeters)
            buffer.putDouble(model.scaleMetersPerModelUnit)
            buffer.putDouble(model.headingDegrees)

            model.assetPath
        }
        buffer.flip()
        return NativePayload(
            numericRecords = buffer,
            assetPaths = assetPaths,
            count = models.size,
        )
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
        numericRecords: ByteBuffer,
        assetPaths: Array<String>,
        count: Int,
    ): Long

    @JvmStatic
    private external fun destroyContextNative(context: Long)
}
