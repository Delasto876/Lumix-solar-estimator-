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
 * [RouteDistanceSource] is the "build now, activate later" seam the project owner explicitly
 * asked for (AskUserQuestion, 2026-08-18: "Integrate real Google Directions API later"). Nothing
 * implements it yet — today's only distance source is the quote's own manually-entered
 * [com.lumix.estimator.domain.QuoteInputs.deliveryRouteDistanceKm], read directly by
 * [SystemCalculator][com.lumix.estimator.domain.SystemCalculator], bypassing this interface
 * entirely. A future phase can add a Google Directions-backed implementation and have the wizard
 * write its result into that same field, without touching this calculator's own formula below.
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
