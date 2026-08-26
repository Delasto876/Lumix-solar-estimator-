package com.lumix.estimator.site.streetview

/**
 * Site Survey / Solar Mapping round (spec "Integrate Street View (where available) for site
 * verification"): every outcome [StreetViewClient.fetchPanoramaImage] can return — mirrors
 * [com.lumix.estimator.site.solarapi.SolarApiResult]'s own three-way shape for the same reason:
 * "no imagery here" (Google's own real, common coverage gap) is distinct from an operational
 * failure (no key configured, network error, malformed response), and neither is ever silently
 * turned into a fabricated or placeholder image presented as real.
 *
 * [Available] is a plain (non-data) class rather than a data class: [ByteArray] doesn't have
 * content-based `equals`/`hashCode`, and this result is never compared for equality or put in a
 * set/map key — it is consumed exactly once (decoded to a bitmap for display), so the compiler-
 * generated reference-based equality Kotlin would otherwise warn about is simply irrelevant here.
 */
sealed class StreetViewResult {
    class Available(val imageBytes: ByteArray) : StreetViewResult()
    data class NoCoverage(val message: String) : StreetViewResult()
    data class Unavailable(val reason: String) : StreetViewResult()
}
