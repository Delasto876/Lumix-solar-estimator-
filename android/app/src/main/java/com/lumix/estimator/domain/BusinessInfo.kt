package com.lumix.estimator.domain

/**
 * A79 (spec Phase 16 — "improve settings/materials", §40's own "Company information / Address /
 * Phone / Email / Default warranty / Payment terms"): the installer's own real business details,
 * entered once in Settings ([com.lumix.estimator.data.SettingsRepository]) and read by every quote
 * export ([com.lumix.estimator.pdf.QuotePdfGenerator], [com.lumix.estimator.export
 * .QuoteHtmlGenerator], [com.lumix.estimator.export.QuoteCsvGenerator]) so all three formats show
 * identical business content instead of each needing its own copy. Every field defaults to blank —
 * see [SettingsRepository]'s own doc for why nothing is pre-filled — and each export renders its
 * corresponding section only when non-blank, per [isBlank].
 */
data class BusinessInfo(
    val companyName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val warranty: String = "",
    val paymentTerms: String = ""
) {
    val isBlank: Boolean get() =
        companyName.isBlank() && address.isBlank() && phone.isBlank() && email.isBlank() && warranty.isBlank() && paymentTerms.isBlank()
}
