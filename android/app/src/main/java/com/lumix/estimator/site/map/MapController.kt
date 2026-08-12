package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumix.estimator.site.GeoPoint

/**
 * OpenStreetMap has no built-in satellite+labels hybrid tile source the way Google Maps does —
 * just the two real options here. STREET uses Mapnik (OSM's standard street map); SATELLITE
 * uses Esri World Imagery, both tile sources requiring no API key.
 */
enum class SiteMapType { STREET, SATELLITE }

/** Map UI state (type, selected pin) — kept separate from roof-drawing state and the simulation engine. */
class MapController {
    var mapType by mutableStateOf(SiteMapType.SATELLITE)
        private set
    var selectedLocation by mutableStateOf<GeoPoint?>(null)
        private set

    fun setMapType(type: SiteMapType) {
        mapType = type
    }

    fun selectLocation(point: GeoPoint) {
        selectedLocation = point
    }

    /** Cycles through the (now two) available tile sources. */
    fun cycleMapType() {
        mapType = when (mapType) {
            SiteMapType.SATELLITE -> SiteMapType.STREET
            SiteMapType.STREET -> SiteMapType.SATELLITE
        }
    }
}
