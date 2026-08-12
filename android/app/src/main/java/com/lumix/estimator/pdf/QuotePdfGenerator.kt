package com.lumix.estimator.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.domain.formatQty
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

    fun generate(context: Context, inputs: QuoteInputs, result: QuoteResult, timestamp: Long): File {
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

        canvas.drawText("Lumix Solar Estimator", MARGIN, y, titlePaint); y += 18f
        canvas.drawText("Solar System Quote", MARGIN, y, mutedPaint); y += 20f

        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
        canvas.drawText("Date: $dateStr", MARGIN, y, bodyPaint); y += 14f
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
            canvas.drawText(
                "Battery: ${"%.1f".format(result.totalBatteryKwh)} kWh (backup ~${"%.0f".format(inputs.backupHours)}h)",
                MARGIN, y, bodyPaint
            ); y += 14f
        }
        y += 6f
        canvas.drawText("Estimated Total: ${formatCurrency(result.grandTotal)}", MARGIN, y, headingPaint); y += 18f

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
            canvas.drawText(formatCurrency(m.unitPrice), colUnit, y, bodyPaint)
            canvas.drawText(formatCurrency(m.subtotal), colSub, y, bodyPaint)
            y += 14f
        }

        ensureSpace(110f)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint); y += 16f
        canvas.drawText("Materials total: ${formatCurrency(result.materialsTotal)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Service (15%): ${formatCurrency(result.serviceCharge)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Delivery: ${formatCurrency(result.deliveryCharge)}", MARGIN, y, bodyPaint); y += 14f
        canvas.drawText("Discount: -${formatCurrency(result.discountAmount)}", MARGIN, y, bodyPaint); y += 16f
        canvas.drawText("Grand Total: ${formatCurrency(result.grandTotal)}", MARGIN, y, totalPaint); y += 20f

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
