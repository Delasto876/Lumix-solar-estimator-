package com.lumix.estimator.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.RoofConstraint
import com.lumix.estimator.domain.SolarResource
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A56 (spec §5–9, 35–36 — "do NOT require quote information before the installer can design and
 * simulate the system... only when CREATE QUOTE is selected should the app ask for quote-specific
 * information"), extended A88 (spec Phase 26 §17 — "Do NOT force the installer to enter every
 * site detail before the system can be sized... show CONTINUE TO SITE DETAILS" after design):
 * DESIGN is every system-sizing step; SITE_DETAILS is the one deferred, non-engineering-critical
 * step (property type/system type/JPS rate/storeys — see [com.lumix.estimator.ui.wizard.steps
 * .StepSiteDetails]'s own doc for why these specific fields, and only these, were confirmed safe
 * to defer past calculation); QUOTE_DETAILS is Customer + Pricing, reached exclusively via
 * `SystemResultScreen`'s CREATE QUOTE button — never a prerequisite for calculating or simulating.
 */
enum class WizardFlowMode { DESIGN, SITE_DETAILS, QUOTE_DETAILS }

class WizardViewModel(
    private val quoteRepository: QuoteRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val _inputs = MutableStateFlow(QuoteInputs())
    val inputs: StateFlow<QuoteInputs> = _inputs.asStateFlow()

    private val _currentStep = MutableStateFlow(2)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _flowMode = MutableStateFlow(WizardFlowMode.DESIGN)
    val flowMode: StateFlow<WizardFlowMode> = _flowMode.asStateFlow()

    private val _result = MutableStateFlow<QuoteResult?>(null)
    val result: StateFlow<QuoteResult?> = _result.asStateFlow()

    private val _savedQuoteId = MutableStateFlow<Long?>(null)
    val savedQuoteId: StateFlow<Long?> = _savedQuoteId.asStateFlow()

    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    // A88 (spec Phase 26): step numbers, per concept. Which of these actually appear, and in what
    // ORDER, now genuinely differs per [com.lumix.estimator.domain.QuoteMode] — see [designSteps]
    // below, not just a filtered ascending range like before this round.
    //  1  Customer (QUOTE_DETAILS)
    //  2  Quote Mode (mode picker)
    //  3  Location (+ system mode — see StepLocation's own doc for why system mode stays here)
    //  4  Roof Type (stays pre-calculation — feeds real mounting-hardware materials math)
    //  5  JPS Bill / Usage (GUIDED only)
    //  6  Manual Mode Type (MANUAL only)
    //  7  Battery Bank (MANUAL only)
    //  8  Inverter (MANUAL only)
    //  9  Panels (MANUAL only)
    //  10 Appliances (all modes — air conditioning folded in, A88 §15)
    //  11 Backup Requirements (GUIDED only — LOAD/MANUAL use QuoteInputs' own 12h/Most-Load defaults)
    //  12 System Review (all modes)
    //  13 Site Details (SITE_DETAILS only — property type/system type/JPS rate/storeys)
    //  14 Pricing & Discount (QUOTE_DETAILS)
    val totalSteps = 14

    fun update(transform: (QuoteInputs) -> QuoteInputs) {
        _inputs.value = transform(_inputs.value)
    }

    fun errorsForStep(step: Int): List<String> {
        val data = _inputs.value
        return when (step) {
            1 -> Validation.customerErrors(data)
            3 -> Validation.customerErrors(data) // parish is asked here now, not just at step 1
            5 -> Validation.usageErrors(data)
            9 -> Validation.manualErrors(data) // panel count, now on the dedicated Panels step
            14 -> Validation.pricingErrors(data)
            else -> emptyList()
        }
    }

    /**
     * A88: genuinely different, explicitly ORDERED step sequences per mode — not a filtered
     * ascending range (the pre-A88 approach), since GUIDED/LOAD-BASED/MANUAL now visit their
     * mode-specific steps in a different relative order (e.g. MANUAL's Battery -> Inverter ->
     * Panels, per spec Phase 26 §11), not just "skip steps that don't apply."
     */
    private fun designSteps(): List<Int> {
        val data = _inputs.value
        return when (data.quoteMode) {
            QuoteMode.GUIDED -> listOf(2, 3, 4, 5, 10, 11, 12)
            QuoteMode.LOAD -> listOf(2, 3, 4, 10, 12)
            QuoteMode.MANUAL -> listOf(2, 3, 4, 6, 7, 8, 9, 10, 12)
        }
    }

    private fun siteDetailsSteps(): List<Int> = listOf(13)

    private fun quoteDetailSteps(): List<Int> = listOf(1, totalSteps)

    fun visibleSteps(): List<Int> = when (_flowMode.value) {
        WizardFlowMode.DESIGN -> designSteps()
        WizardFlowMode.SITE_DETAILS -> siteDetailsSteps()
        WizardFlowMode.QUOTE_DETAILS -> quoteDetailSteps()
    }

    fun goNext(): Boolean {
        val errors = errorsForStep(_currentStep.value)
        if (errors.isNotEmpty()) return false
        val visible = visibleSteps()
        val idx = visible.indexOf(_currentStep.value)
        if (idx in 0 until visible.lastIndex) {
            _currentStep.value = visible[idx + 1]
        }
        return true
    }

    fun goBack() {
        val visible = visibleSteps()
        val idx = visible.indexOf(_currentStep.value)
        if (idx > 0) {
            _currentStep.value = visible[idx - 1]
        }
    }

    /** A49 — MANUAL mode's "CHANGE INVERTER"/"CHANGE BATTERY" review warnings jump straight to the relevant step. */
    fun goToStep(step: Int) {
        if (step in visibleSteps()) {
            _currentStep.value = step
        }
    }

    fun isLastStep(): Boolean {
        val visible = visibleSteps()
        return visible.indexOf(_currentStep.value) == visible.lastIndex
    }

    /**
     * A56: entered from `SystemResultScreen`'s CREATE QUOTE button. Design inputs (already
     * calculated and saved via [calculateAndSave]) are untouched — this only switches which steps
     * are visible, so [calculateAndSave] finishes the SAME saved row rather than starting over.
     */
    fun startQuoteDetails() {
        _flowMode.value = WizardFlowMode.QUOTE_DETAILS
        _currentStep.value = 1
    }

    /**
     * A88 (spec Phase 26 §17): entered from `SystemResultScreen`'s "Continue to Site Details"
     * button — a small, optional, ALREADY-DESIGNED-system side excursion, mirroring
     * [startQuoteDetails]'s own pattern rather than being wedged into [designSteps]'s own array
     * (which would have changed what "last step" means for the DESIGN flow's Calculate System
     * trigger — see the A88 README section for why that path was rejected).
     */
    fun openSiteDetails() {
        _flowMode.value = WizardFlowMode.SITE_DETAILS
        _currentStep.value = 13
    }

    /**
     * A88: persists whatever Site Details fields the installer changed — none of them affect
     * [QuoteResult] (confirmed not read by [SystemCalculator] — see [com.lumix.estimator.ui.wizard
     * .steps.StepSiteDetails]'s own doc), so this updates the saved row's inputs only, without
     * recalculating. No-ops (calls [onDone] with nothing to persist) if this quote was never
     * calculated/saved yet, which shouldn't be reachable in practice since Site Details is only
     * ever opened from the post-calculation `SystemResultScreen`.
     */
    fun saveSiteDetails(onDone: (Long) -> Unit) {
        val id = _savedQuoteId.value
        val calc = _result.value
        if (id == null || calc == null) return
        viewModelScope.launch {
            quoteRepository.update(id, _inputs.value, calc)
            onDone(id)
        }
    }

    fun reset() {
        _inputs.value = QuoteInputs()
        _flowMode.value = WizardFlowMode.DESIGN
        _currentStep.value = 2
        _result.value = null
        _savedQuoteId.value = null
    }

    /** A81 (Phase 18, restored): starts a fresh quote pre-loaded with a Solar Site roof's panel-fit limit, so the wizard's recommendation is capped at what the roof can physically hold. */
    /**
     * "Do NOT make the map a disconnected visual feature" (2026-08-18): [parish]/[town] come from
     * the map's reverse-geocoded selection (see `SolarSiteMapScreen`'s "Use This Location" flow),
     * so a roof traced on the map now carries the same parish/PSH auto-fill `StepLocation` gives a
     * manually-typed location — not just lat/lon for panel-count capping.
     */
    fun startWithRoofConstraint(constraint: RoofConstraint, parish: String? = null, town: String? = null) {
        reset()
        _inputs.value = _inputs.value.let { current ->
            var updated = current.copy(roofConstraint = constraint)
            if (!parish.isNullOrBlank()) {
                updated = updated.copy(parish = parish, nearestTown = town.orEmpty())
                if (!updated.peakSunHoursManuallySet) {
                    updated = updated.copy(peakSunHours = SolarResource.estimatedPshFor(parish))
                }
            }
            updated
        }
    }

    /**
     * A76 (spec Phase 13 — "add editable design + Recalculate"): the only way back into the
     * wizard with an ALREADY-SAVED quote's real inputs loaded, instead of a blank [QuoteInputs].
     * Before this, a quote opened from Home/History (`ResultsScreen`) had no edit path at all —
     * only "New quote" (blank slate) or read-only viewing; the existing "Edit System" button on
     * `SystemResultScreen` only worked because the wizard's own state was still live in memory
     * from the same design session. Lands on step 12 (System Review) — the same screen "Edit
     * System" already returns to, and always in [designSteps] regardless of quote mode — so
     * pressing Back walks through every earlier step to change anything, and pressing "Calculate
     * System" re-runs the full engineering pipeline. [_savedQuoteId] being non-null here is what
     * makes [calculateAndSave] update this SAME row instead of creating a duplicate.
     */
    fun loadForEdit(saved: SavedQuote) {
        _inputs.value = saved.inputs
        _result.value = saved.result
        _savedQuoteId.value = saved.id
        _flowMode.value = WizardFlowMode.DESIGN
        _currentStep.value = 12
    }

    /**
     * A56: saves a preliminary row the first time (no customer/discount yet — DESIGN flow's
     * "Calculate System"), then overwrites that SAME row once QUOTE_DETAILS finishes ("Save
     * Quote") — see [com.lumix.estimator.data.QuoteRepository.update]'s own doc for why this must
     * never become a second row for the same project.
     */
    fun calculateAndSave(onDone: (Long) -> Unit) {
        viewModelScope.launch {
            _isCalculating.value = true
            val prices = priceRepository.prices.first()
            val data = _inputs.value
            val calc = SystemCalculator.calculate(data, prices)
            _result.value = calc
            val existingId = _savedQuoteId.value
            val id = if (existingId != null) {
                quoteRepository.update(existingId, data, calc)
                existingId
            } else {
                quoteRepository.save(data, calc)
            }
            _savedQuoteId.value = id
            _isCalculating.value = false
            onDone(id)
        }
    }

    companion object {
        fun factory(quoteRepository: QuoteRepository, priceRepository: PriceRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WizardViewModel(quoteRepository, priceRepository) as T
                }
            }
    }
}
