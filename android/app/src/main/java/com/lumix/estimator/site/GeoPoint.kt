package com.lumix.estimator.site

/** A WGS84 coordinate. Every roof/site coordinate in this module is a real map coordinate — never a screen-relative point. */
data class GeoPoint(val latitude: Double, val longitude: Double)
