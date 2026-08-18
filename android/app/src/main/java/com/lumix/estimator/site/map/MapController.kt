package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumix.estimator.site.GeoPoint

/**
 * "The application should support: MAP, SATELLITE as separate layers" (2026-08-18) — replaces the
 * earlier NORMAL/SATELLITE/HYBRID trio (a Google Maps SDK concept) now that satellite imagery is
 * its own, currently-unconfigured [com.lumix.estimator.map.SatelliteProvider] rather than a mode
 * the base map itself can render.
 */
enum class MapLayer { MAP, SATELLITE }

/** Map UI state (layer, selected pin, tilt) — kept separate from roof-drawing state and the simulation engine. */
class MapController {
    var layer by mutableStateOf(MapLayer.MAP)
        private set
    var selectedLocation by mutableStateOf<GeoPoint?>(null)
        private set
    var is3D by mutableStateOf(false)
        private set

    fun setLayer(newLayer: MapLayer) {
        layer = newLayer
    }

    fun selectLocation(point: GeoPoint) {
        selectedLocation = point
    }

    /** A close, tilted view reads more like standing on the roof than a flat top-down map. */
    fun toggle3D() {
        is3D = !is3D
    }

    companion object {
        const val TILT_3D_DEGREES = 55.0
        const val TILT_FLAT_DEGREES = 0.0
    }
}
