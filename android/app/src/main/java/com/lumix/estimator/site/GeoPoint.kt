package com.lumix.estimator.site

import kotlinx.serialization.Serializable

/** A WGS84 coordinate. Every roof/site coordinate in this module is a real map coordinate — never a screen-relative point. */
@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double)
