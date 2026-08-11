package com.lumix.estimator.domain

import java.text.DecimalFormat

private val currencyFormat = DecimalFormat("#,##0")
private val qtyFormat = DecimalFormat("#,##0.##")

fun formatCurrency(amount: Double): String = "J$" + currencyFormat.format(amount)

fun formatQty(qty: Double): String = qtyFormat.format(qty)
