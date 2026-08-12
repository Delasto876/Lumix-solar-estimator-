package com.lumix.estimator.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.RoofConstraint
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WizardViewModel(
    private val quoteRepository: QuoteRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val _inputs = MutableStateFlow(QuoteInputs())
    val inputs: StateFlow<QuoteInputs> = _inputs.asStateFlow()

    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _result = MutableStateFlow<QuoteResult?>(null)
    val result: StateFlow<QuoteResult?> = _result.asStateFlow()

    private val _savedQuoteId = MutableStateFlow<Long?>(null)
    val savedQuoteId: StateFlow<Long?> = _savedQuoteId.asStateFlow()

    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    // 1 Customer, 2 Mode & Site Info, 3 Roof & Mounting, 4 Loads, 5 JPS Bill/Usage (GUIDED only),
    // 6 Backup Requirements, 7 Manual System Builder (MANUAL only), 8 Pricing & Discount.
    val totalSteps = 8

    fun update(transform: (QuoteInputs) -> QuoteInputs) {
        _inputs.value = transform(_inputs.value)
    }

    fun errorsForStep(step: Int): List<String> {
        val data = _inputs.value
        return when (step) {
            1 -> Validation.customerErrors(data)
            5 -> Validation.usageErrors(data)
            7 -> Validation.manualErrors(data)
            8 -> Validation.pricingErrors(data)
            else -> emptyList()
        }
    }

    fun visibleSteps(): List<Int> {
        val mode = _inputs.value.quoteMode
        val steps = (1..totalSteps).toMutableList()
        if (mode == QuoteMode.LOAD) steps.remove(5)
        if (mode != QuoteMode.MANUAL) steps.remove(7)
        return steps
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

    fun isLastStep(): Boolean {
        val visible = visibleSteps()
        return visible.indexOf(_currentStep.value) == visible.lastIndex
    }

    fun reset() {
        _inputs.value = QuoteInputs()
        _currentStep.value = 1
        _result.value = null
        _savedQuoteId.value = null
    }

    /** Starts a fresh quote pre-loaded with a Solar Site roof's panel-fit limit, so the wizard's recommendation is capped at what the roof can physically hold. */
    fun startWithRoofConstraint(constraint: RoofConstraint) {
        reset()
        _inputs.value = _inputs.value.copy(roofConstraint = constraint)
    }

    fun calculateAndSave(onDone: (Long) -> Unit) {
        viewModelScope.launch {
            _isCalculating.value = true
            val regular = priceRepository.regularPrices.first()
            val discount = priceRepository.discountPrices.first()
            val data = _inputs.value
            val calc = SystemCalculator.calculate(data, regular, discount)
            _result.value = calc
            val id = quoteRepository.save(data, calc)
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
