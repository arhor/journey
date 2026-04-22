package com.github.arhor.journey.feature.map.viewinterop

import android.graphics.Color
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
import com.github.arhor.journey.feature.map.fow.model.fogOfWarLayerSpecs
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.spatialk.geojson.toJson

internal const val EMPTY_NATIVE_FOG_GEO_JSON = """{"type":"FeatureCollection","features":[]}"""

internal class NativeFogOfWarLayerController {

    private var style: Style? = null
    private var lastState: FogOfWarRenderState = FogOfWarRenderState()
    private val lastGeoJsonBySourceId = mutableMapOf<String, String>()
    private val lastVisibilityByLayerId = mutableMapOf<String, Boolean>()

    fun attach(style: Style) {
        this.style = style
        ensureLayers(style)
        applyState(style, lastState)
    }

    fun update(state: FogOfWarRenderState) {
        lastState = state
        style?.let { applyState(it, state) }
    }

    private fun ensureLayers(style: Style) {
        for (spec in lastState.fogOfWarLayerSpecs()) {
            if (style.getSourceAs<GeoJsonSource>(spec.sourceId) == null) {
                style.addSource(GeoJsonSource(spec.sourceId, EMPTY_NATIVE_FOG_GEO_JSON))
            }
            if (style.getLayerAs<FillLayer>(spec.layerId) == null) {
                style.addLayer(
                    FillLayer(spec.layerId, spec.sourceId)
                        .withProperties(
                            fillColor(Color.BLACK),
                            fillOpacity(spec.opacity),
                            visibility(if (spec.isVisible) Property.VISIBLE else Property.NONE),
                        ),
                )
            }
        }
    }

    private fun applyState(style: Style, state: FogOfWarRenderState) {
        for (spec in state.fogOfWarLayerSpecs()) {
            val source = style.getSourceAs<GeoJsonSource>(spec.sourceId)
            val layer = style.getLayerAs<FillLayer>(spec.layerId)
            val geoJson = spec.renderData.toNativeFogGeoJson()

            if (source != null && lastGeoJsonBySourceId[spec.sourceId] != geoJson) {
                source.setGeoJson(geoJson)
                lastGeoJsonBySourceId[spec.sourceId] = geoJson
            }

            if (layer != null && lastVisibilityByLayerId[spec.layerId] != spec.isVisible) {
                layer.setProperties(
                    visibility(if (spec.isVisible) Property.VISIBLE else Property.NONE),
                )
                lastVisibilityByLayerId[spec.layerId] = spec.isVisible
            }
        }
    }
}

internal fun FogOfWarRenderData?.toNativeFogGeoJson(): String =
    this?.geoJsonData?.geoJson?.toJson() ?: EMPTY_NATIVE_FOG_GEO_JSON
