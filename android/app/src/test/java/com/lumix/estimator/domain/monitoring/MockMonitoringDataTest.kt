package com.lumix.estimator.domain.monitoring

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A85 (Phase 23 continuation): regression tests for [MockMonitoringData]/[MockMonitoringAlerts] —
 * confirms the mock generator produces a plausible day curve (zero PV at night, positive PV at
 * solar noon, SOC always in range, timestamp echoed back) rather than a broken or nonsensical
 * shape, and that [MockMonitoringProvider] always reports Connected (never NotConfigured, since
 * mock data always "works").
 */
class MockMonitoringDataTest {

    private fun atHour(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `PV power is zero at midnight and positive at solar noon`() {
        val midnight = MockMonitoringData.generate(MonitoringManufacturer.DEYE, atHour(0))
        val noon = MockMonitoringData.generate(MonitoringManufacturer.DEYE, atHour(12))
        assertEquals(0.0, midnight.pvPower, 0.0)
        assertTrue("expected positive PV at solar noon, got ${noon.pvPower}", noon.pvPower > 0.0)
    }

    @Test
    fun `battery SOC always stays within 0 to 100`() {
        for (hour in 0..23) {
            val telemetry = MockMonitoringData.generate(MonitoringManufacturer.LUXPOWER, atHour(hour))
            assertTrue("SOC out of range at hour $hour: ${telemetry.batterySoc}", telemetry.batterySoc in 0f..100f)
        }
    }

    @Test
    fun `timestamp echoes the requested instant`() {
        val at = atHour(9)
        val telemetry = MockMonitoringData.generate(MonitoringManufacturer.GROWATT, at)
        assertEquals(at, telemetry.timestamp)
    }

    @Test
    fun `energyTotal is populated (unlike SimulatedMonitoringProvider) since mock data mirrors a real manufacturer API shape`() {
        val telemetry = MockMonitoringData.generate(MonitoringManufacturer.SOLARMAN, atHour(12))
        assertTrue(telemetry.energyTotal != null && telemetry.energyTotal!! > 0.0)
    }

    @Test
    fun `low battery SOC produces a warning alert`() {
        val lowSoc = DeviceTelemetry(
            pvPower = 0.0, pvVoltage = 0.0, pvCurrent = 0.0,
            batterySoc = 10f, batteryPower = -1.0, batteryVoltage = 48.0,
            loadPower = 1.0, gridPower = 0.5, energyToday = 5.0, energyTotal = 1000.0,
            faultCode = null, temperature = 28.0, timestamp = 0L
        )
        val alerts = MockMonitoringAlerts.forTelemetry(lowSoc)
        assertTrue(alerts.any { it.severity == MockMonitoringAlert.Severity.CRITICAL })
    }

    @Test
    fun `fetchLatest always reports Connected, never NotConfigured`() {
        val provider = MockMonitoringProvider(MonitoringManufacturer.SOLAR_OF_THINGS)
        val result = kotlinx.coroutines.runBlocking { provider.fetchLatest("mock-device") }
        assertTrue(result is MonitoringResult.Connected)
        assertEquals(MonitoringManufacturer.SOLAR_OF_THINGS, provider.manufacturer)
    }
}
