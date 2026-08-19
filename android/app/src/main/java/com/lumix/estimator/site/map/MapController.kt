package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumix.estimator.site.GeoPoint

/**
 * Map UI state (selected pin, tilt) — kept separate from roof-drawing state and the simulation
 * engine. 2026-08-19 ("change map to google map"): the earlier MAP/SATELLITE `MapLayer` enum this
 * class tracked is gone — base-map style switching is now Google Maps' own native `MapType`
 * (NORMAL/SATELLITE/TERRAIN/HYBRID), tracked locally in [SolarSiteMapScreen] the same way
 * `selectedStyleUrl` was for the MapLibre style switcher, not duplicated here.
 */
class MapController {
    var selectedLocation by mutableStateOf<GeoPoint?>(null)
        private set
    var is3D by mutableStateOf(false)
        private set

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
