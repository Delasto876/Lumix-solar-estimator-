package com.lumix.estimator.domain

import java.text.DecimalFormat

private val currencyFormat = DecimalFormat("#,##0")
private val qtyFormat = DecimalFormat("#,##0.##")

fun formatCurrency(amount: Double): String = "J$" + currencyFormat.format(amount)

fun formatQty(qty: Double): String = qtyFormat.format(qty)

/**
 * A78 (spec Phase 15 — "improve quote engine", §38's own "quote number" requirement): a stable,
 * human-readable quote number derived from the saved quote's real database id — every screen and
 * export format (PDF/HTML/CSV) that shows a quote number reads this same function, so the number
 * printed on a shared PDF always matches what History/Settings would show for that same row.
 * Deliberately NOT a separately-tracked sequence — the row's own id is already unique and stable
 * for the row's lifetime, so a second counter would only be one more thing that could drift from it.
 */
fun quoteNumberFor(id: Long): String = "LMX-Q-%05d".format(id)

/** A78 (spec Phase 15, §38's own "validity" requirement): 30 days is a placeholder default, not a confirmed Lumix policy — see Phase 16's own Settings scope for making this configurable. */
const val DEFAULT_QUOTE_VALIDITY_DAYS = 30

/** Quote validity end date, formatted for display — [issuedAtMillis] + [DEFAULT_QUOTE_VALIDITY_DAYS]. */
fun quoteValidUntil(issuedAtMillis: Long): java.util.Date =
    java.util.Date(issuedAtMillis + DEFAULT_QUOTE_VALIDITY_DAYS.toLong() * 24 * 60 * 60 * 1000)
