package com.lumix.estimator.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lumix.estimator.domain.BusinessInfo
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.domain.formatQty
import com.lumix.estimator.domain.quoteNumberFor
import com.lumix.estimator.domain.quoteValidUntil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A78 (spec Phase 15 — "improve quote engine", §38's "Allow: PDF, HTML, CSV where appropriate"):
 * an HTML rendering of the exact same [QuoteResult]/[QuoteInputs] data [com.lumix.estimator.pdf
 * .QuotePdfGenerator] already renders as PDF — same content, a browser-viewable/printable format
 * instead. Not a second source of quote content: every figure here reads the same already-computed
 * fields (materials, subtotalBeforeDiscount, discountAmount, taxAmount, grandTotal), so this can
 * never show a different total than the PDF for the same saved quote.
 */
object QuoteHtmlGenerator {

    fun generate(context: Context, quoteId: Long, inputs: QuoteInputs, result: QuoteResult, timestamp: Long, business: BusinessInfo = BusinessInfo()): File {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val validUntilFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val backupLabel = if (result.totalBatteryKwh > 0) {
            if (result.estimatedBackupSufficient) "${result.estimatedBackupHours.toInt()}+ h" else "~%.1f h".format(result.estimatedBackupHours)
        } else null
        // A79 (spec Phase 16): see QuotePdfGenerator's identical fix for why this reads the real
        // effective rate instead of a hard-coded "15%".
        val servicePercentLabel = if (result.materialsTotal > 0) {
            " (%.0f%%)".format(result.serviceCharge / result.materialsTotal * 100.0)
        } else ""

        val html = buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Quote ${quoteNumberFor(quoteId)}</title>")
            append(
                "<style>" +
                    "body{font-family:sans-serif;color:#0F172A;padding:24px;max-width:720px;margin:0 auto;}" +
                    "h1{font-size:20px;margin-bottom:2px;} .muted{color:#6B7280;font-size:13px;}" +
                    "table{width:100%;border-collapse:collapse;margin-top:12px;}" +
                    "th,td{text-align:left;padding:6px 4px;border-bottom:1px solid #E5E7EB;font-size:13px;}" +
                    "th{font-size:12px;text-transform:uppercase;color:#6B7280;}" +
                    ".totals td{border-bottom:none;padding:3px 4px;} .grand{font-weight:bold;font-size:16px;}" +
                    "</style></head><body>"
            )
            append("<h1>Lumix Technologies</h1><div class=\"muted\">Solar System Quote</div>")
            // A79 (spec Phase 16, §40): only rendered once the installer has filled these in.
            listOfNotNull(
                business.address.takeIf { it.isNotBlank() },
                business.phone.takeIf { it.isNotBlank() },
                business.email.takeIf { it.isNotBlank() }
            ).forEach { line -> append("<div class=\"muted\">$line</div>") }
            append("<p>Quote ${quoteNumberFor(quoteId)}<br>")
            append("Date: ${dateFormat.format(Date(timestamp))} &middot; Valid until: ${validUntilFormat.format(quoteValidUntil(timestamp))}")
            if (inputs.customerName.isNotBlank()) append("<br>Customer: ${inputs.customerName}")
            if (inputs.customerContact.isNotBlank()) append("<br>Contact: ${inputs.customerContact}")
            val location = listOf(inputs.nearestTown, inputs.parish).filter { it.isNotBlank() }.joinToString(", ")
            if (location.isNotBlank()) append("<br>Location: $location")
            append("</p>")

            append("<h2>System Summary</h2><ul>")
            append("<li>PV Array: ${result.panelCount} × ${result.panelWatts}W (${"%.2f".format(result.pvKw)} kW)</li>")
            append("<li>Inverter: ${result.inverterName}</li>")
            if (backupLabel != null) {
                append("<li>Battery: ${"%.1f".format(result.totalBatteryKwh)} kWh (estimated backup $backupLabel)</li>")
            }
            append("</ul>")

            append("<h2>Material Breakdown</h2><table><thead><tr><th>Item</th><th>Qty</th><th>Unit</th><th>Subtotal</th></tr></thead><tbody>")
            result.materials.forEach { m ->
                // A89/Ph21: never format a missing price as currency — see MaterialLine.unitPrice's own doc.
                val unitCell = if (m.hasPrice) formatCurrency(m.unitPrice!!) else "<strong>Price not entered</strong>"
                val subCell = if (m.hasPrice) formatCurrency(m.subtotal) else "-"
                append("<tr><td>${m.name}</td><td>${formatQty(m.qty)}</td><td>$unitCell</td><td>$subCell</td></tr>")
            }
            if (!result.canFinalize) {
                append("<p style=\"color:#b91c1c;font-weight:bold;\">This quote cannot be finalized: ${result.missingPriceItems.joinToString(", ")} — price not yet entered.</p>")
            }
            append("</tbody></table>")

            append("<table class=\"totals\">")
            append("<tr><td>Materials total</td><td>${formatCurrency(result.materialsTotal)}</td></tr>")
            append("<tr><td>Service$servicePercentLabel</td><td>${formatCurrency(result.serviceCharge)}</td></tr>")
            append("<tr><td>Delivery</td><td>${formatCurrency(result.deliveryCharge)}</td></tr>")
            append("<tr><td>Subtotal</td><td>${formatCurrency(result.subtotalBeforeDiscount)}</td></tr>")
            append("<tr><td>Discount</td><td>-${formatCurrency(result.discountAmount)}</td></tr>")
            append("<tr><td>Tax/fees</td><td>${formatCurrency(result.taxAmount)}</td></tr>")
            append("<tr class=\"grand\"><td>Grand Total</td><td>${formatCurrency(result.grandTotal)}</td></tr>")
            append("</table>")

            // A79 (spec Phase 16, §40): only rendered once the installer has filled these in.
            if (business.warranty.isNotBlank()) append("<h2>Warranty</h2><p>${business.warranty}</p>")
            if (business.paymentTerms.isNotBlank()) append("<h2>Payment Terms</h2><p>${business.paymentTerms}</p>")

            append("<p class=\"muted\">This is an estimate. Final pricing may vary after a site visit.</p>")
            append("</body></html>")
        }

        val dir = File(context.filesDir, "quotes").apply { mkdirs() }
        val file = File(dir, "quote_$timestamp.html")
        file.writeText(html)
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
