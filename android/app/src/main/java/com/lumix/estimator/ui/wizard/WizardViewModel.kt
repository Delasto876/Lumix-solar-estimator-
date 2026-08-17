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
 * information"): which half of the wizard's step numbers is currently active. DESIGN is every
 * system-sizing step (2 through 12 — Quote Mode through System Review); QUOTE_DETAILS is only
 * steps 1 (Customer) and 13 (Pricing & Discount), reached exclusively via `SystemResultScreen`'s
 * CREATE QUOTE button — never a prerequisite for calculating or simulating a system.
 */
enum class WizardFlowMode { DESIGN, QUOTE_DETAILS }

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

    // One concept per screen, per the mobile usability pass: 1 Customer, 2 Quote Mode,
    // 3 Property & System, 4 Roof Type, 5 Air Conditioning, 6 Household Appliances,
    // 7 JPS Bill/Usage (GUIDED only), 8 Backup Requirements, 9 Manual Mode (MANUAL only),
    // 10 Inverter & Panels (MANUAL only), 11 Battery Bank (MANUAL only), 12 System Review,
    // 13 Pricing & Discount. (The old "Roof Mounting / 3-rail" step was removed — zinc roofs
    // just always use 3 rails/row now, its own prior default, rather than asking.) A56: 1 and 13
    // moved out of the normal forward sequence into [WizardFlowMode.QUOTE_DETAILS] — see that
    // enum's own doc.
    val totalSteps = 13

    fun update(transform: (QuoteInputs) -> QuoteInputs) {
        _inputs.value = transform(_inputs.value)
    }

    fun errorsForStep(step: Int): List<String> {
        val data = _inputs.value
        return when (step) {
            1 -> Validation.customerErrors(data)
            7 -> Validation.usageErrors(data)
            10 -> Validation.manualErrors(data)
            13 -> Validation.pricingErrors(data)
            else -> emptyList()
        }
    }

    private fun designSteps(): List<Int> {
        val data = _inputs.value
        val steps = (2 until totalSteps).toMutableList() // 2..12 — every sizing/design step
        // JPS Bill/Usage only makes sense in GUIDED mode — LOAD mode sizes from appliance load
        // directly, MANUAL mode has its own explicit sizing steps. (Previously this only
        // excluded LOAD mode, leaving a blank page reachable in MANUAL mode's step count.)
        if (data.quoteMode != QuoteMode.GUIDED) steps.remove(7)
        if (data.quoteMode != QuoteMode.MANUAL) {
            steps.remove(9)
            steps.remove(10)
            steps.remove(11)
        }
        return steps
    }

    private fun quoteDetailSteps(): List<Int> = listOf(1, totalSteps)

    fun visibleSteps(): List<Int> = when (_flowMode.value) {
        WizardFlowMode.DESIGN -> designSteps()
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

    fun reset() {
        _inputs.value = QuoteInputs()
        _flowMode.value = WizardFlowMode.DESIGN
        _currentStep.value = 2
        _result.value = null
        _savedQuoteId.value = null
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
