package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLng

enum class SiteMapType { NORMAL, SATELLITE, HYBRID }

/** Map UI state (type, selected pin) — kept separate from roof-drawing state and the simulation engine. */
class MapController {
    var mapType by mutableStateOf(SiteMapType.SATELLITE)
        private set
    var selectedLocation by mutableStateOf<LatLng?>(null)
        private set

    fun setMapType(type: SiteMapType) {
        mapType = type
    }

    fun selectLocation(latLng: LatLng) {
        selectedLocation = latLng
    }
}
