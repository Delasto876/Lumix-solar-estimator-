package com.lumix.estimator.domain

object Validation {
    fun customerErrors(input: QuoteInputs): List<String> {
        val errors = mutableListOf<String>()
        if (input.parish.isBlank()) errors += "Please select a parish."
        return errors
    }

    fun usageErrors(input: QuoteInputs): List<String> {
        if (input.quoteMode != QuoteMode.GUIDED) return emptyList()
        val errors = mutableListOf<String>()
        when (input.usageMode) {
            UsageMode.BILL -> if (input.avgBill <= 0) errors += "Enter your average JPS bill amount."
            UsageMode.KWH -> if (input.avgKwh <= 0) errors += "Enter your average monthly kWh usage."
            UsageMode.UNKNOWN -> {}
        }
        return errors
    }

    fun manualErrors(input: QuoteInputs): List<String> {
        if (input.quoteMode != QuoteMode.MANUAL) return emptyList()
        val errors = mutableListOf<String>()
        if (input.manualModeType != ManualModeType.BATTERY_LED && input.manualPanelCount <= 0) {
            errors += "Enter a number of panels, or switch to Battery-led mode."
        }
        return errors
    }

    fun pricingErrors(input: QuoteInputs): List<String> {
        val errors = mutableListOf<String>()
        if (input.discountType == DiscountType.PERCENT && (input.discountValue < 0 || input.discountValue > 100)) {
            errors += "Percent discount must be between 0 and 100."
        } else if (input.discountValue < 0) {
            errors += "Discount value can't be negative."
        }
        if (input.deliveryCharge < 0) errors += "Delivery charge can't be negative."
        if (input.installationCommissioningCharge < 0) errors += "Installation & commissioning charge can't be negative."
        return errors
    }
}
