package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLng

enum class SiteMapType { NORMAL, SATELLITE, HYBRID }

/** Map UI state (type, selected pin, tilt) — kept separate from roof-drawing state and the simulation engine. */
class MapController {
    var mapType by mutableStateOf(SiteMapType.SATELLITE)
        private set
    var selectedLocation by mutableStateOf<LatLng?>(null)
        private set
    var is3D by mutableStateOf(false)
        private set

    fun setMapType(type: SiteMapType) {
        mapType = type
    }

    fun selectLocation(latLng: LatLng) {
        selectedLocation = latLng
    }

    /** A close, tilted satellite view reads more like standing on the roof than a flat top-down map. */
    fun toggle3D() {
        is3D = !is3D
    }

    companion object {
        const val TILT_3D_DEGREES = 55f
        const val TILT_FLAT_DEGREES = 0f
    }
}
