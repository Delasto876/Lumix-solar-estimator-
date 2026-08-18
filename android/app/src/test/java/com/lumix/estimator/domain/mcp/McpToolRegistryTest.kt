package com.lumix.estimator.domain.mcp

import com.lumix.estimator.domain.DiagnosticCheck
import com.lumix.estimator.domain.MaterialLine
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.domain.simulation.TechnicalModel
import com.lumix.estimator.domain.ai.AiExplanationResult
import com.lumix.estimator.domain.ai.EngineeringExplanationContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A85 (Phase 24): confirms every [McpToolRegistry] tool is a faithful, unmodified projection of
 * real domain data — no invented figures, no mutation — using the same real
 * [SimulationEngine]/[TechnicalModel]-computed fixtures [SimulatedMonitoringProviderTest] already
 * established are genuine (not placeholders).
 */
class McpToolRegistryTest {

    private fun quote() = QuoteResult(
        effectiveSystemMode = SystemMode.HYBRID,
        designDailyKwh = 20.0,
        peakWatts = 3000.0,
        panelCount = 6,
        panelWatts = 615,
        inverterName = "Deye SUN-10K-SG01LP1-US",
        inverterKw = 10.0,
        batteryName = "10kWh (SRNE SR-EOS10B)",
        batteryRequiredKwh = 10.0,
        totalBatteryKwh = 10.24,
        rows = 2,
        railsPerRow = 2,
        totalRails = 4,
        totalMidClamps = 8,
        totalEndClamps = 4,
        totalBackLegs = 6,
        totalFrontLegs = 6,
        totalBolts = 20,
        totalLFoot = 12,
        materials = listOf(MaterialLine("Panel", 6.0, 300.0), MaterialLine("Rail", 4.0, 50.0)),
        materialsTotal = 2000.0,
        serviceCharge = 200.0,
        deliveryCharge = 100.0,
        discountAmount = 50.0,
        grandTotal = 2250.0
    )

    @Test
    fun `getSystemDesign projects real QuoteResult fields, not invented values`() {
        val q = quote()
        val design = McpToolRegistry.getSystemDesign(q)
        assertEquals(q.pvKw, design.pvKw, 0.0)
        assertEquals(q.panelCount, design.panelCount)
        assertEquals(q.inverterName, design.inverterName)
        assertEquals(q.batteryName, design.batteryName)
        assertEquals(q.isRoofConstrained, design.isRoofConstrained)
    }

    @Test
    fun `getCustomer reads QuoteInputs customer fields verbatim`() {
        val inputs = QuoteInputs(customerName = "Jane Doe", customerContact = "876-555-0100")
        val customer = McpToolRegistry.getCustomer(inputs)
        assertEquals("Jane Doe", customer.name)
        assertEquals("876-555-0100", customer.contact)
    }

    @Test
    fun `getQuote and getMaterialTakeoff never diverge from the source QuoteResult totals`() {
        val q = quote()
        val summary = McpToolRegistry.getQuote(q)
        assertEquals(q.grandTotal, summary.grandTotal, 0.0)
        assertEquals(q.materialsTotal, summary.materialsTotal, 0.0)

        val takeoff = McpToolRegistry.getMaterialTakeoff(q)
        assertEquals(q.materials, takeoff.lines)
        assertEquals(q.materialsTotal, takeoff.materialsTotal, 0.0)
    }

    @Test
    fun `getSystemWarnings marks allPassed true only when every check passed`() {
        val allPass = listOf(DiagnosticCheck("A", true, null), DiagnosticCheck("B", true, null))
        assertTrue(McpToolRegistry.getSystemWarnings(allPass).allPassed)

        val oneFail = listOf(DiagnosticCheck("A", true, null), DiagnosticCheck("B", false, "undersized"))
        val warnings = McpToolRegistry.getSystemWarnings(oneFail)
        assertEquals(false, warnings.allPassed)
        assertEquals(1, warnings.failing.size)
        assertEquals("undersized", warnings.failing.first().detail)
    }

    @Test
    fun `getBatteryStatus and getInverterStatus read the real TechnicalReadout, not placeholders`() {
        val config = SimSystemConfig(
            pvCapacityKw = 6 * 0.615, panelCount = 6, panelWatts = 615,
            inverterKw = 10.0, inverterName = "Deye SUN-10K-SG01LP1-US",
            batteryCapacityKwh = 10.24, batteryName = "10kWh (SRNE SR-EOS10B)",
            hasBattery = true, gridConnectable = true, avgDailyLoadKwh = 20.0, peakLoadKw = 3.0,
            batteryMaxChargeKw = 7.68, batteryMaxDischargeKw = 10.0, batteryChargeEfficiency = 0.95,
            batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
        )
        val timeline = SimulationEngine.buildDayTimeline(config, gridConnected = true, startSocFraction = 0.6)
        val frame = SimulationEngine.frameAt(timeline, 12.0)
        val readout = TechnicalModel.compute(frame, config, timeline, gridServiceAmps = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS, dayType = com.lumix.estimator.domain.simulation.DayType.WEEKDAY)
        val q = quote()

        val battery = McpToolRegistry.getBatteryStatus(q, readout)
        assertEquals(readout.batterySocPercent, battery.socPercent, 0.0f)
        assertEquals(readout.batteryVoltage, battery.voltage, 0.0)

        val inverter = McpToolRegistry.getInverterStatus(q, readout)
        assertEquals(readout.inverterOutputKw, inverter.outputKw, 0.0)
        assertEquals(q.inverterName, inverter.name)

        val simState = McpToolRegistry.getSimulationState(frame)
        assertEquals(frame.pvKw, simState.pvKw, 0.0)
        assertEquals(frame.status.name, simState.status)
    }

    @Test
    fun `explainCalculation delegates to AiExplanationService and returns Disabled by default`() {
        val q = quote()
        val context = EngineeringExplanationContext(quote = q, diagnostics = emptyList())
        val result = runBlocking { McpToolRegistry.explainCalculation(context) }
        assertEquals(AiExplanationResult.Disabled, result)
    }
}
