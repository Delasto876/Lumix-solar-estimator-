package com.lumix.estimator.map

/**
 * Aerial/satellite imagery — deliberately a SEPARATE abstraction from [BaseMapProvider] per the
 * project owner's explicit instruction: "Do not pretend that OpenFreeMap provides satellite
 * imagery. Keep the base-map provider separate from the satellite imagery provider." OpenFreeMap
 * is a vector road-map style only; it has no aerial photography layer.
 *
 * No implementation is wired in yet — "Do not implement a paid provider yet." [NoSatelliteProvider]
 * is the current default, always reporting unavailable. A future Google Maps/Esri/MapTiler/other
 * licensed-imagery integration only has to implement this interface and be swapped in at the one
 * call site that reads it; nothing about the drawing/geocoding/routing code depends on which
 * satellite provider (if any) is configured.
 */
interface SatelliteProvider {
    val name: String
    val isConfigured: Boolean
    /** A MapLibre-compatible raster/style source URL for satellite imagery, or null when not configured — never a fabricated URL. */
    fun styleUrlOrNull(): String?
}

object NoSatelliteProvider : SatelliteProvider {
    override val name: String = "None configured"
    override val isConfigured: Boolean = false
    override fun styleUrlOrNull(): String? = null
}
