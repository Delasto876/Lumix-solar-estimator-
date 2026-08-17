package com.lumix.estimator.domain.monitoring

import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.domain.simulation.TechnicalModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A83 (spec Phase 22, original §63 — "FUTURE MONITORING"): regression tests for
 * [SimulatedMonitoringProvider]/[DeviceTelemetry] — confirms the normalized-model mapping reads
 * real figures already computed by [TechnicalModel]/[SimulationEngine] (not invented placeholder
 * numbers) for every field this app actually tracks, and stays null for the two fields it
 * doesn't ([DeviceTelemetry.energyTotal]/[DeviceTelemetry.faultCode]) — plus that every named
 * manufacturer in [MonitoringProviderRegistry] honestly reports [MonitoringResult.NotConfigured].
 */
class SimulatedMonitoringProviderTest {

    private fun configFor() = SimSystemConfig(
        pvCapacityKw = 6 * 0.615,
        panelCount = 6,
        panelWatts = 615,
        inverterKw = 10.0,
        inverterName = "Deye SUN-10K-SG01LP1-US",
        batteryCapacityKwh = 10.24,
        batteryName = "10kWh (SRNE SR-EOS10B)",
        hasBattery = true,
        gridConnectable = true,
        avgDailyLoadKwh = 20.0,
        peakLoadKw = 3.0,
        batteryMaxChargeKw = 7.68,
        batteryMaxDischargeKw = 10.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
    )

    @Test
    fun `toDeviceTelemetry maps every tracked field from the real TechnicalReadout, not a placeholder`() {
        val config = configFor()
        val timeline = SimulationEngine.buildDayTimeline(config, gridConnected = true, startSocFraction = 0.6)
        val frame = SimulationEngine.frameAt(timeline, 12.0) // solar noon-ish
        val readout = TechnicalModel.compute(frame, config, timeline, gridServiceAmps = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS, dayType = DayType.WEEKDAY)

        val telemetry = SimulatedMonitoringProvider.toDeviceTelemetry(readout, frame, timestampMillis = 123456789L)

        assertEquals(readout.pvPowerKw, telemetry.pvPower, 0.0)
        assertEquals(readout.pvVoltage, telemetry.pvVoltage, 0.0)
        assertEquals(readout.pvCurrent, telemetry.pvCurrent, 0.0)
        assertEquals(readout.batterySocPercent, telemetry.batterySoc, 0.0f)
        assertEquals(frame.batteryPowerKw, telemetry.batteryPower, 0.0)
        assertEquals(readout.batteryVoltage, telemetry.batteryVoltage, 0.0)
        assertEquals(frame.houseLoadKw, telemetry.loadPower, 0.0)
        assertEquals(frame.gridPowerKw, telemetry.gridPower, 0.0)
        assertEquals(readout.energyTodayKwh, telemetry.energyToday, 0.0)
        assertEquals(readout.cellTempC, telemetry.temperature, 0.0)
        assertEquals(123456789L, telemetry.timestamp)
    }

    @Test
    fun `energyTotal and faultCode are always null - this app tracks neither`() {
        val config = configFor()
        val timeline = SimulationEngine.buildDayTimeline(config, gridConnected = true, startSocFraction = 0.6)
        val frame = SimulationEngine.frameAt(timeline, 12.0)
        val readout = TechnicalModel.compute(frame, config, timeline, gridServiceAmps = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS, dayType = DayType.WEEKDAY)

        val telemetry = SimulatedMonitoringProvider.toDeviceTelemetry(readout, frame)
        assertNull(telemetry.energyTotal)
        assertNull(telemetry.faultCode)
    }

    @Test
    fun `fetchLatest returns a Connected result with real PV output at solar noon`() {
        val config = configFor()
        val timeline = SimulationEngine.buildDayTimeline(config, gridConnected = true, startSocFraction = 0.6)
        val frame = SimulationEngine.frameAt(timeline, 12.0)
        val provider = SimulatedMonitoringProvider(
            config = config, timeline = timeline, frame = frame,
            gridServiceAmps = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS
        )

        val result = runBlocking { provider.fetchLatest("demo-device") }
        assertTrue(result is MonitoringResult.Connected)
        val telemetry = (result as MonitoringResult.Connected).telemetry
        assertTrue("expected real PV output near solar noon, got ${telemetry.pvPower}", telemetry.pvPower > 0.5)
        assertEquals(null, provider.manufacturer)
    }

    @Test
    fun `every named manufacturer honestly reports NotConfigured, never a fabricated reading`() {
        for (manufacturer in MonitoringManufacturer.entries) {
            val provider = MonitoringProviderRegistry.providerFor(manufacturer)
            assertEquals(manufacturer, provider.manufacturer)
            val result = runBlocking { provider.fetchLatest("any-device-id") }
            assertEquals(MonitoringResult.NotConfigured, result)
        }
    }
}
