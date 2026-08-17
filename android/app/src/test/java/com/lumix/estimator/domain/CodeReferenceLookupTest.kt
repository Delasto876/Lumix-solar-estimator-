package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A82 (spec Phase 19 — "electrical-code lookup architecture", "if the required standards are not
 * present: say 'Source document required for verification'"): regression tests for
 * [CodeReferenceLookup] — pure lookup logic, no Android `Context` dependency (unlike
 * [com.lumix.estimator.data.CodeStandardRepository], which stores these via DataStore and is
 * therefore untested at the unit level, consistent with this project's existing pattern for
 * DataStore-backed repositories) — plus a guard that [SystemDiagnostics.ALL_CHECK_LABELS] can
 * never silently drift from the labels [SystemDiagnostics.checksFor] actually produces, since a
 * citation entered against a label that no longer exists would silently stop resolving.
 */
class CodeReferenceLookupTest {

    private val necStandard = CodeStandard(
        id = "std_1", name = "NEC", edition = "2023",
        sourceNote = "Purchased copy, office binder", addedAtMillis = 0L
    )

    private val vocReference = CodeRequirementReference(
        id = "ref_1", standardId = "std_1",
        checkLabel = SystemDiagnostics.LABEL_VOC,
        sectionArticle = "690.7", relevanceNote = "Max PV system voltage"
    )

    @Test
    fun `no standards on file returns SourceRequired`() {
        val result = CodeReferenceLookup.referenceFor(SystemDiagnostics.LABEL_VOC, emptyList(), emptyList())
        assertEquals(CodeReferenceResult.SourceRequired, result)
    }

    @Test
    fun `a citation with no matching check label returns SourceRequired`() {
        val result = CodeReferenceLookup.referenceFor(
            "some other check", listOf(necStandard), listOf(vocReference)
        )
        assertEquals(CodeReferenceResult.SourceRequired, result)
    }

    @Test
    fun `a citation whose standard was deleted returns SourceRequired, not a dangling reference`() {
        val result = CodeReferenceLookup.referenceFor(SystemDiagnostics.LABEL_VOC, emptyList(), listOf(vocReference))
        assertEquals(CodeReferenceResult.SourceRequired, result)
    }

    @Test
    fun `a real citation against an existing standard is Found with both objects attached`() {
        val result = CodeReferenceLookup.referenceFor(SystemDiagnostics.LABEL_VOC, listOf(necStandard), listOf(vocReference))
        assertTrue(result is CodeReferenceResult.Found)
        val found = result as CodeReferenceResult.Found
        assertEquals(necStandard, found.standard)
        assertEquals(vocReference, found.reference)
    }

    @Test
    fun `ALL_CHECK_LABELS never drifts from what checksFor actually produces`() {
        // A minimal but real QuoteResult — the exact numbers don't matter, only that every label
        // checksFor emits is one of the fixed set an installer can pick from in Settings.
        val result = QuoteResult(
            effectiveSystemMode = SystemMode.HYBRID,
            designDailyKwh = 10.0,
            peakWatts = 2000.0,
            panelCount = 6,
            panelWatts = 615,
            inverterName = "Test Inverter",
            inverterKw = 6.0,
            batteryName = "Test Battery",
            batteryRequiredKwh = 5.0,
            totalBatteryKwh = 10.0,
            rows = 1,
            railsPerRow = 2,
            totalRails = 2,
            totalMidClamps = 4,
            totalEndClamps = 4,
            totalBackLegs = 6,
            totalFrontLegs = 6,
            totalBolts = 20,
            totalLFoot = 6,
            materials = emptyList(),
            materialsTotal = 0.0,
            serviceCharge = 0.0,
            deliveryCharge = 0.0,
            discountAmount = 0.0,
            grandTotal = 0.0
        )
        val labels = SystemDiagnostics.checksFor(result, targetBackupHours = 12.0).map { it.label }
        assertEquals(labels.toSet(), SystemDiagnostics.ALL_CHECK_LABELS.toSet())
        assertEquals("checksFor's own emission order should match ALL_CHECK_LABELS's declared order", labels, SystemDiagnostics.ALL_CHECK_LABELS)
    }
}
