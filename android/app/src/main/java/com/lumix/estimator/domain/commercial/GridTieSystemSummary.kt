package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterSpec

/**
 * Phase 51 (Inverter Engine spec, "PARALLEL GRID-TIE... Show: Number of inverters, kW per inverter,
 * Total AC kW, Panels per inverter, Strings per inverter, Strings per MPPT, Total PV kW, Total AC
 * current, Transformer requirement, Transformer voltage, Transformer kVA, AC protection, DC
 * protection"): a pure, read-only summary assembled from data that already exists elsewhere in the
 * domain layer — [ParallelInverterDesign] (Phase 27), the resolved real
 * [EquipmentSpecs.InverterSpec] (Phase 48), and [GridTieTransformerAdvisor] (Phase 49). This object
 * makes no sizing decisions of its own; it only collects and formats numbers the spec's own "Show:"
 * list asks for into one place, for [StepCommercialIndustrialDesign]'s grid-tie summary card.
 */
object GridTieSystemSummary {

    /** Same NEC continuous-load convention (125% of the continuous load) [GridTieTransformerAdvisor] already documents — applied here to AC breaker/disconnect sizing guidance, not to an invented figure. */
    private const val CONTINUOUS_DUTY_MARGIN = 1.25

    data class Summary(
        val inverterCount: Int,
        val kwPerInverter: Double,
        val totalAcKw: Double,
        val panelsPerInverter: Int,
        val stringsPerInverter: Int,
        val stringsPerMppt: Int,
        val totalPvKw: Double,
        /** Null when the resolved inverter has no real [EquipmentSpecs.InverterSpec.acOutputA] on file — never a guessed current. */
        val totalAcCurrentA: Double?,
        val transformer: GridTieTransformerAdvisor.Recommendation?,
        val acProtection: String,
        val dcProtection: String
    )

    /**
     * [inverterSpec] should be the real catalog entry behind [design.inverterModelId] (resolved by
     * the caller, e.g. `EquipmentSpecs.inverters.firstOrNull { it.model == design.inverterModelId }`)
     * — null whenever the installer typed a custom/unrecognized model name, in which case every
     * datasheet-derived field below (current, transformer, protection guidance) degrades to "not on
     * file" rather than a guess, while the pure count/kW/panel figures — which only depend on
     * [design] itself — still compute normally.
     */
    fun summarize(design: ParallelInverterDesign, inverterSpec: InverterSpec?, site: ElectricalService): Summary {
        val unit = design.unitPvDesigns.firstOrNull()
        val panelsPerInverter = unit?.totalPanels ?: 0
        val stringsPerInverter = unit?.strings?.size ?: 0
        val stringsPerMppt = unit?.strings
            ?.groupBy { it.mpptIndex }
            ?.values
            ?.maxOfOrNull { it.size } ?: 0

        val totalAcCurrentA = inverterSpec?.acOutputA?.let { it.toDouble() * design.inverterCount }

        val transformer = inverterSpec?.let { spec ->
            GridTieTransformerAdvisor.recommend(spec, design.inverterCount, site)
        }

        val ratedAcA = inverterSpec?.acOutputA
        val acProtection = if (ratedAcA != null) {
            val recommendedA = ratedAcA * CONTINUOUS_DUTY_MARGIN
            "AC disconnect/breaker per unit sized at least %.1f A (125%% of this model's real %d A rated AC output, NEC continuous-load convention) x %d unit(s), plus this model's own built-in anti-islanding/surge/reverse-polarity protection (see catalog data)."
                .format(recommendedA, ratedAcA, design.inverterCount)
        } else {
            "This inverter has no real rated AC output current on file — confirm AC disconnect/breaker sizing against the manufacturer's own datasheet before procurement."
        }

        val maxScA = inverterSpec?.maxShortCircuitCurrentPerMpptA
        val mpptCount = inverterSpec?.mpptCount
        val dcProtection = if (maxScA != null && mpptCount != null) {
            "Per-string DC fusing/protection rated for at least this model's own real %.1f A max short-circuit current per MPPT input (%d MPPT trackers), and DC disconnect rated for the array's max system voltage (up to %sV DC per this model's datasheet)."
                .format(maxScA, mpptCount, inverterSpec?.maxPvV?.toString() ?: "an unconfirmed")
        } else {
            "This inverter has no real max short-circuit current per MPPT on file — confirm DC fusing/disconnect sizing against the manufacturer's own datasheet before procurement."
        }

        return Summary(
            inverterCount = design.inverterCount,
            kwPerInverter = design.ratedKwPerUnit,
            totalAcKw = design.totalInverterCapacityKw,
            panelsPerInverter = panelsPerInverter,
            stringsPerInverter = stringsPerInverter,
            stringsPerMppt = stringsPerMppt,
            totalPvKw = design.totalPvKw,
            totalAcCurrentA = totalAcCurrentA,
            transformer = transformer,
            acProtection = acProtection,
            dcProtection = dcProtection
        )
    }
}
