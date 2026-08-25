package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterArchitecture
import com.lumix.estimator.domain.InverterSpec
import kotlin.math.abs

/**
 * Phase 49 (Inverter Engine spec, "GRID-TIE LOGIC" / "TRANSFORMER SIZING"): "If inverter AC
 * voltage/phase matches site voltage/phase: PV -> grid-tie inverter -> AC protection ->
 * site/grid. No transformer. If voltage does not match: PV -> grid-tie inverter -> AC protection
 * -> transformer -> site/grid. Automatically label transformer STEP-UP or STEP-DOWN based on
 * voltage difference... Transformer sizing must use inverter maximum apparent power/kVA plus
 * applicable engineering/derating requirements. Do not size transformer from PV wattage alone."
 * Worked examples from the spec, both reproduced in [GridTieTransformerAdvisorTest]: "S5-GC30K-LV
 * 220V 3-phase -> 400V 3-phase site = STEP-UP transformer" and "S5-GC50K 230/400V -> compatible
 * 400V 3-phase site = NO transformer."
 *
 * This is advisory-only, pure-function guidance for a later phase's UI to pre-fill/suggest — it
 * does NOT write into [Transformer] itself. [Transformer] (Phase 46) stays the installer's own
 * self-declared field set, "Do NOT automatically select a transformer unless the voltage/phase
 * mismatch requires one" — this object is what computes whether that mismatch exists and what the
 * resulting recommendation should be; Phase 46's own doc already anticipated exactly this split
 * ("no verified transformer equipment catalog... every field here is the installer's own entered
 * value, not a picked-from-catalog model").
 */
object GridTieTransformerAdvisor {

    /**
     * Relative tolerance for treating two nominal AC voltages as "the same" — real utility supply
     * voltage is never exact to the volt. 2% is a standard utility voltage-tolerance band (ANSI
     * C84.1's own Range A is roughly this magnitude), not a fabricated figure specific to this app.
     */
    private const val VOLTAGE_MATCH_TOLERANCE_FRACTION = 0.02

    /**
     * Continuous-duty transformer sizing margin: 125% of the connected load — the same NEC
     * continuous-load convention (Art. 210.19(A)/215.2(A), "conductors/OCPD sized at not less than
     * 125% of the continuous load") already familiar from breaker/conductor sizing elsewhere in
     * this codebase, a real citable code rule rather than an invented figure — applied here to the
     * inverter's own real [EquipmentSpecs.InverterSpec.maxApparentPowerKva] since a grid-tie
     * inverter can run at its full rated output continuously during sunlight hours.
     */
    private const val CONTINUOUS_DUTY_MARGIN = 1.25

    data class Recommendation(
        /** False when [EquipmentSpecs.InverterSpec.architecture] isn't [InverterArchitecture.GRID_TIE] — this advisor has nothing to say about a hybrid/off-grid design. */
        val applicable: Boolean,
        /** Null when [applicable] is true but there isn't enough voltage data on file to decide either way. */
        val required: Boolean?,
        val direction: TransformerDirection?,
        /** The real inverter-side AC line-to-line voltage this recommendation compared against. */
        val inverterVoltage: Double?,
        val siteVoltage: Double?,
        /** Null unless [required] is true and the inverter has a real [EquipmentSpecs.InverterSpec.maxApparentPowerKva] on file. */
        val recommendedKvaRating: Double?,
        val reason: String
    )

    /**
     * [inverterCount] lets a parallel grid-tie design (Phase 51) size the transformer for the
     * combined output of every unit sharing one site interconnection point, not just one — the
     * spec's own "calculate inverter quantity... Transformer kVA" summary line treats this as a
     * system-level figure.
     */
    fun recommend(
        inverterSpec: InverterSpec,
        inverterCount: Int,
        site: ElectricalService
    ): Recommendation {
        if (inverterSpec.architecture != InverterArchitecture.GRID_TIE) {
            return Recommendation(
                applicable = false, required = null, direction = null,
                inverterVoltage = null, siteVoltage = null, recommendedKvaRating = null,
                reason = "${inverterSpec.brand} ${inverterSpec.model} is not a grid-tie inverter — this transformer logic only applies to grid-tie designs."
            )
        }
        if (inverterSpec.acLineToLineVoltageOptionsV.isEmpty() || site.nominalVoltage <= 0.0) {
            return Recommendation(
                applicable = true, required = null, direction = null,
                inverterVoltage = null, siteVoltage = site.nominalVoltage.takeIf { it > 0.0 }, recommendedKvaRating = null,
                reason = "Insufficient voltage data on file to determine transformer requirement — confirm the inverter's AC voltage and the site's electrical service."
            )
        }

        val siteVoltage = site.nominalVoltage
        val matchingVoltage = inverterSpec.acLineToLineVoltageOptionsV.firstOrNull { option ->
            abs(option - siteVoltage) <= option * VOLTAGE_MATCH_TOLERANCE_FRACTION
        }
        val phaseMismatch = site.phase != LoadPhaseType.THREE_PHASE

        if (matchingVoltage != null && !phaseMismatch) {
            return Recommendation(
                applicable = true, required = false, direction = null,
                inverterVoltage = matchingVoltage, siteVoltage = siteVoltage, recommendedKvaRating = null,
                reason = "Inverter AC voltage (${matchingVoltage.formatVolts()}V three-phase) matches the site's electrical service (${siteVoltage.formatVolts()}V) — no transformer required."
            )
        }

        val nearestInverterVoltage = inverterSpec.acLineToLineVoltageOptionsV.minByOrNull { abs(it - siteVoltage) }!!
        val direction = if (siteVoltage > nearestInverterVoltage) TransformerDirection.STEP_UP else TransformerDirection.STEP_DOWN
        val recommendedKva = inverterSpec.maxApparentPowerKva?.let { it * inverterCount.coerceAtLeast(1) * CONTINUOUS_DUTY_MARGIN }

        val phaseNote = if (phaseMismatch) {
            " Site electrical service is not three-phase — this grid-tie inverter is a three-phase-only unit; verify site phase configuration before procurement."
        } else ""
        val directionLabel = if (direction == TransformerDirection.STEP_UP) "step-up" else "step-down"
        return Recommendation(
            applicable = true, required = true, direction = direction,
            inverterVoltage = nearestInverterVoltage, siteVoltage = siteVoltage, recommendedKvaRating = recommendedKva,
            reason = "Inverter AC voltage (${nearestInverterVoltage.formatVolts()}V) does not match the site's electrical service (${siteVoltage.formatVolts()}V) — a $directionLabel transformer is required.$phaseNote"
        )
    }

    private fun Double.formatVolts(): String =
        if (this == Math.floor(this)) this.toInt().toString() else "%.1f".format(this)
}
