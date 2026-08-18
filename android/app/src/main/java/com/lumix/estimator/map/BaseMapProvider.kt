package com.lumix.estimator.map

/**
 * "REPLACE THE CURRENT MAP IMPLEMENTATION" (2026-08-18): the base (non-satellite) map tile/style
 * source. [OpenFreeMapProvider] is the only implementation — a free, no-API-key, no-billing
 * public style service — but the app never references it directly outside this file/the map
 * screen's provider wiring, so a self-hosted or alternate free style source can be swapped in
 * later without touching the drawing/geocoding/routing code around it.
 */
interface BaseMapProvider {
    val name: String
    /** A MapLibre GL style JSON URL — the same style format MapLibre Native (this app) and MapLibre GL JS (web) both consume, so this URL is not provider-specific. */
    val styleUrl: String
    val attribution: String
}

/**
 * The project owner's own explicit choice: "Use the OpenFreeMap public map service for the base
 * map... https://tiles.openfreemap.org/styles/liberty... Do NOT require: Google Maps API, Google
 * billing, Google Maps API key, Mapbox API key." OpenFreeMap serves pre-built vector tiles from
 * OpenStreetMap data at no cost and with no account/key required.
 */
object OpenFreeMapProvider : BaseMapProvider {
    override val name: String = "OpenFreeMap (Liberty)"
    override val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty"
    override val attribution: String = "© OpenStreetMap contributors · OpenFreeMap"
}
