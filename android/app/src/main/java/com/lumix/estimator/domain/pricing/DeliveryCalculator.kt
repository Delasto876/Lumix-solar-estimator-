package com.lumix.estimator.domain.pricing

import com.lumix.estimator.domain.PriceList

/**
 * A89/Ph21 (master prompt §"DELIVERY"): "using the distance from Junction St. Elizabeth to Santa
 * Cruz 28km to judge all other distance except for toll routes... for that distance I would
 * charge 18k." The spreadsheet's own Delivery & Tolls sheet formalizes this as a proportional
 * formula (`DEL-RATE`): `baseCharge x (routeDistanceKm / baselineDistanceKm)`. Toll is added
 * separately, ONLY on a route that actually crosses a toll — never folded into the proportional
 * base rate, and never invented when the toll amount hasn't been entered (see [tollMissing]).
 *
 * A89/Ph21 follow-up (2026-08-18 — "let me manually enter delivery price"): the project owner
 * chose to enter [com.lumix.estimator.domain.QuoteInputs.deliveryCharge] manually for every quote
 * instead — [SystemCalculator][com.lumix.estimator.domain.SystemCalculator] no longer calls
 * [calculate] to compute/override that field. This object and its formula stay built (a "build
 * now, activate later" seam, matching the project owner's own earlier "Integrate real Google
 * Directions API later" choice) for whenever automatic distance-based pricing is wanted again —
 * [RouteDistanceSource] is the documented, currently-unimplemented seam for a future routing-API
 * lookup that would feed [calculate]'s `distanceKm` parameter.
 */
fun interface RouteDistanceSource {
    fun distanceKm(fromDescription: String, toDescription: String): Double
}

data class DeliveryResult(
    val distanceKm: Double,
    val baseCharge: Double,
    val isTollRoute: Boolean,
    /** Null only when [isTollRoute] is true and the toll rate hasn't been entered yet — see [tollMissing]. Always 0.0 (not null) when [isTollRoute] is false, since no toll applies at all. */
    val tollCharge: Double?
) {
    val tollMissing: Boolean get() = isTollRoute && tollCharge == null
    /** Null (not a silently-zeroed number) whenever [tollMissing] — callers must check that first, per the master prompt's "never calculate blank as zero" rule. */
    val totalCharge: Double? get() = if (tollMissing) null else baseCharge + (tollCharge ?: 0.0)
}

object DeliveryCalculator {
    /**
     * [distanceKm] is the one-way route distance for this specific quote (already resolved —
     * manually entered today, a future [RouteDistanceSource] implementation's job later, not this
     * function's). Proportional to [PriceList.deliveryBaseChargeJmd] at
     * [PriceList.deliveryBaselineDistanceKm] — exactly reproduces JMD 18,000 when [distanceKm]
     * equals the 28km baseline itself, per the spreadsheet's own worked example.
     */
    fun calculate(distanceKm: Double, isTollRoute: Boolean, prices: PriceList): DeliveryResult {
        val baseline = prices.deliveryBaselineDistanceKm.takeIf { it > 0.0 } ?: 28.0
        val safeDistanceKm = distanceKm.coerceAtLeast(0.0)
        val baseCharge = prices.deliveryBaseChargeJmd * (safeDistanceKm / baseline)
        val tollCharge = if (isTollRoute) prices.deliveryTollJmd else 0.0
        return DeliveryResult(
            distanceKm = safeDistanceKm,
            baseCharge = baseCharge,
            isTollRoute = isTollRoute,
            tollCharge = tollCharge
        )
    }
}
