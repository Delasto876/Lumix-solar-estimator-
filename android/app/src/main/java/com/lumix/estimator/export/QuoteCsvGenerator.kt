package com.lumix.estimator.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.quoteNumberFor
import com.lumix.estimator.domain.quoteValidUntil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A78 (spec Phase 15 — "improve quote engine", §38's "Allow: PDF, HTML, CSV where appropriate"):
 * a plain-numbers CSV export of the exact same [QuoteResult] data [com.lumix.estimator.pdf
 * .QuotePdfGenerator]/[QuoteHtmlGenerator] already render, for the installer's own
 * spreadsheet/accounting workflow rather than a customer-facing document.
 */
object QuoteCsvGenerator {

    fun generate(context: Context, quoteId: Long, inputs: QuoteInputs, result: QuoteResult, timestamp: Long): File {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val validUntilFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val csv = buildString {
            appendLine(csvRow("Quote Number", quoteNumberFor(quoteId)))
            appendLine(csvRow("Date", dateFormat.format(Date(timestamp))))
            appendLine(csvRow("Valid Until", validUntilFormat.format(quoteValidUntil(timestamp))))
            appendLine(csvRow("Customer", inputs.customerName))
            appendLine(csvRow("Contact", inputs.customerContact))
            val location = listOf(inputs.nearestTown, inputs.parish).filter { it.isNotBlank() }.joinToString(", ")
            appendLine(csvRow("Location", location))
            appendLine(csvRow("PV Array", "${result.panelCount} x ${result.panelWatts}W (${"%.2f".format(result.pvKw)} kW)"))
            appendLine(csvRow("Inverter", result.inverterName))
            if (result.totalBatteryKwh > 0) {
                appendLine(csvRow("Battery", "%.1f kWh".format(result.totalBatteryKwh)))
            }
            appendLine()

            appendLine(csvRow("Item", "Qty", "Unit Price", "Subtotal"))
            result.materials.forEach { m ->
                appendLine(csvRow(m.name, "%.2f".format(m.qty), "%.2f".format(m.unitPrice), "%.2f".format(m.subtotal)))
            }
            appendLine()

            appendLine(csvRow("Materials Total", "%.2f".format(result.materialsTotal)))
            appendLine(csvRow("Service (15%)", "%.2f".format(result.serviceCharge)))
            appendLine(csvRow("Delivery", "%.2f".format(result.deliveryCharge)))
            appendLine(csvRow("Subtotal", "%.2f".format(result.subtotalBeforeDiscount)))
            appendLine(csvRow("Discount", "-%.2f".format(result.discountAmount)))
            appendLine(csvRow("Tax/Fees", "%.2f".format(result.taxAmount)))
            appendLine(csvRow("Grand Total", "%.2f".format(result.grandTotal)))
        }

        val dir = File(context.filesDir, "quotes").apply { mkdirs() }
        val file = File(dir, "quote_$timestamp.csv")
        file.writeText(csv)
        return file
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    private fun csvRow(vararg fields: String): String = fields.joinToString(",") { csvField(it) }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
