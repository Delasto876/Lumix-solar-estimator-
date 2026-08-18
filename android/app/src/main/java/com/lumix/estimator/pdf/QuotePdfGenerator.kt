package com.lumix.estimator.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.lumix.estimator.domain.BusinessInfo
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.domain.formatQty
import com.lumix.estimator.domain.quoteNumberFor
import com.lumix.estimator.domain.quoteValidUntil
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QuotePdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    private val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = 0xFF0F172A.toInt() }
    private val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = 0xFF0F172A.toInt() }
    private val tableHeaderPaint = Paint().apply { textSize = 9.5f; isFakeBoldText = true; color = 0xFF0F172A.toInt() }
    private val totalPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = 0xFF0F172A.toInt() }
    private val bodyPaint = Paint().apply { textSize = 10.5f; color = 0xFF111827.toInt() }
    private val mutedPaint = Paint().apply { textSize = 9f; color = 0xFF6B7280.toInt() }
    private val linePaint = Paint().apply { color = 0xFFE5E7EB.toInt(); strokeWidth = 1f }
    // A81 (Phase 18, restored): matches ui.theme.LumixColors.SolarAmberOnLight — the same amber
    // ResultsScreen's RoofConstraintBanner uses, so a roof-constrained quote reads consistently
    // on-screen and on paper.
    private val warningPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; color = 0xFF9A5A12.toInt() }

    /** Greedy word-wrap so a paragraph fits within [maxWidth] on this Paint's own font metrics. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    fun generate(context: Context, quoteId: Long, inputs: QuoteInputs, result: QuoteResult, timestamp: Long, business: BusinessInfo = BusinessInfo()): File {
        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        canvas.drawText("Lumix Technologies", MARGIN, y, titlePaint); y += 18f
        canvas.drawText("Solar System Quote", MARGIN, y, mutedPaint); y += 14f
        // A79 (spec Phase 16, §40): only rendered once the installer has filled these in via
        // Settings — see BusinessInfo's own doc for why nothing is pre-filled/fabricated.
        listOfNotNull(
            business.address.takeIf { it.isNotBlank() },
            business.phone.takeIf { it.isNotBlank() },
            business.email.takeIf { it.isNotBlank() }
        ).forEach { line -> canvas.drawText(line, MARGIN, y, mutedPaint); y += 12f }
        y += 6f

        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
        val validUntilStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(quoteValidUntil(timestamp))
        canvas.drawText("Quote ${quoteNumberFor(quoteId)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Date: $dateStr  ·  Valid until: $validUntilStr", MARGIN, y, bodyPaint); y += 14f
        if (inputs.customerName.isNotBlank()) {
            canvas.drawText("Customer: ${inputs.customerName}", MARGIN, y, bodyPaint); y += 14f
        }
        if (inputs.customerContact.isNotBlank()) {
            canvas.drawText("Contact: ${inputs.customerContact}", MARGIN, y, bodyPaint); y += 14f
        }
        val location = listOf(inputs.nearestTown, inputs.parish).filter { it.isNotBlank() }.joinToString(", ")
        if (location.isNotBlank()) {
            canvas.drawText("Location: $location", MARGIN, y, bodyPaint); y += 14f
        }
        canvas.drawText("Property: ${inputs.propertyType.label}", MARGIN, y, bodyPaint); y += 14f
        val modeLabel = result.effectiveSystemMode.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }
        canvas.drawText("System: $modeLabel", MARGIN, y, bodyPaint); y += 18f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint); y += 16f

        canvas.drawText("System Summary", MARGIN, y, headingPaint); y += 16f
        canvas.drawText("PV Array: ${result.panelCount} x ${result.panelWatts}W (${"%.2f".format(result.pvKw)} kW)", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Inverter: ${result.inverterName}", MARGIN, y, bodyPaint); y += 14f
        if (result.totalBatteryKwh > 0) {
            // A54: the simulated estimate (BackupEstimator, computed once in SystemCalculator),
            // not a separately recomputed figure — matches what System Review/Results show.
            val backupLabel = if (result.estimatedBackupSufficient) "${result.estimatedBackupHours.toInt()}+h" else "~${"%.1f".format(result.estimatedBackupHours)}h"
            canvas.drawText(
                "Battery: ${"%.1f".format(result.totalBatteryKwh)} kWh (estimated backup $backupLabel)",
                MARGIN, y, bodyPaint
            ); y += 14f
        }
        y += 6f
        canvas.drawText("Estimated Total: ${formatCurrency(result.grandTotal)}", MARGIN, y, headingPaint); y += 18f

        // A81 (Phase 18, restored): mirrors ResultsScreen's RoofConstraintBanner.
        if (result.isRoofConstrained) {
            y += 4f
            ensureSpace(14f)
            canvas.drawText("Roof-Constrained System", MARGIN, y, warningPaint); y += 15f
            val roofLabel = inputs.roofConstraint?.roofLabel ?: "your traced roof"
            val explanation = "Your electricity usage calls for about %.1f kW, but %s can physically fit about %.1f kW (%d panels). Showing the roof-constrained system below."
                .format(result.energyOptimalPvKw, roofLabel, result.pvKw, result.panelCount)
            wrapText(explanation, bodyPaint, PAGE_WIDTH - 2 * MARGIN).forEach { line ->
                ensureSpace(13f)
                canvas.drawText(line, MARGIN, y, bodyPaint); y += 13f
            }
            val suggestion = "Consider tracing additional roof area, adding a second roof plane, or ground mounting to close the gap."
            wrapText(suggestion, mutedPaint, PAGE_WIDTH - 2 * MARGIN).forEach { line ->
                ensureSpace(12f)
                canvas.drawText(line, MARGIN, y, mutedPaint); y += 12f
            }
            y += 8f
        }

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint); y += 16f

        canvas.drawText("Material Breakdown", MARGIN, y, headingPaint); y += 16f

        val colItem = MARGIN
        val colQty = PAGE_WIDTH - MARGIN - 200f
        val colUnit = PAGE_WIDTH - MARGIN - 140f
        val colSub = PAGE_WIDTH - MARGIN - 70f

        ensureSpace(20f)
        canvas.drawText("Item", colItem, y, tableHeaderPaint)
        canvas.drawText("Qty", colQty, y, tableHeaderPaint)
        canvas.drawText("Unit", colUnit, y, tableHeaderPaint)
        canvas.drawText("Subtotal", colSub, y, tableHeaderPaint)
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f

        result.materials.forEach { m ->
            ensureSpace(14f)
            canvas.drawText(truncate(m.name, 40), colItem, y, bodyPaint)
            canvas.drawText(formatQty(m.qty), colQty, y, bodyPaint)
            // A89/Ph21: "NEVER INVENT A PRICE... A BLANK PRICE MUST ALWAYS REMAIN BLANK" — never
            // formatCurrency(0.0) a missing price; see MaterialLine.unitPrice's own doc.
            canvas.drawText(if (m.hasPrice) formatCurrency(m.unitPrice!!) else "Price not entered", colUnit, y, bodyPaint)
            canvas.drawText(if (m.hasPrice) formatCurrency(m.subtotal) else "-", colSub, y, bodyPaint)
            y += 14f
        }

        ensureSpace(140f)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint); y += 16f
        // A79 (spec Phase 16): the service rate is now configurable (PriceList.serviceRatePercent)
        // — this label reads the real effective percent from the two already-computed fields
        // rather than a hard-coded "15%" that would go stale the moment Settings changes the rate.
        val servicePercentLabel = if (result.materialsTotal > 0) {
            " (%.0f%%)".format(result.serviceCharge / result.materialsTotal * 100.0)
        } else ""
        canvas.drawText("Materials total: ${formatCurrency(result.materialsTotal)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Service$servicePercentLabel: ${formatCurrency(result.serviceCharge)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Delivery: ${formatCurrency(result.deliveryCharge)}", MARGIN, y, bodyPaint); y += 14f
        // A78 (spec Phase 15, §39 "Show: Original subtotal, Discount, Final subtotal, Tax/fees,
        // Grand total"): subtotalBeforeDiscount/taxAmount are the same fields SystemCalculator
        // already computed once — never re-added here from the three lines above.
        canvas.drawText("Subtotal: ${formatCurrency(result.subtotalBeforeDiscount)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Discount: -${formatCurrency(result.discountAmount)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Tax/fees: ${formatCurrency(result.taxAmount)}", MARGIN, y, bodyPaint); y += 16f
        canvas.drawText("Grand Total: ${formatCurrency(result.grandTotal)}", MARGIN, y, totalPaint); y += 24f

        // A79 (spec Phase 16, §40): only rendered once the installer has filled these in.
        if (business.warranty.isNotBlank()) {
            ensureSpace(30f)
            canvas.drawText("Warranty", MARGIN, y, headingPaint); y += 14f
            canvas.drawText(business.warranty, MARGIN, y, bodyPaint); y += 18f
        }
        if (business.paymentTerms.isNotBlank()) {
            ensureSpace(30f)
            canvas.drawText("Payment Terms", MARGIN, y, headingPaint); y += 14f
            canvas.drawText(business.paymentTerms, MARGIN, y, bodyPaint); y += 18f
        }

        ensureSpace(14f)
        canvas.drawText("This is an estimate. Final pricing may vary after a site visit.", MARGIN, y, mutedPaint)

        document.finishPage(page)

        val dir = File(context.filesDir, "quotes").apply { mkdirs() }
        val file = File(dir, "quote_$timestamp.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun truncate(s: String, max: Int) = if (s.length > max) s.take(max - 1) + "…" else s

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
