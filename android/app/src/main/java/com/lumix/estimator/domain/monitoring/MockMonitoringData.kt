package com.lumix.estimator.domain.monitoring

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A85 (Phase 23 continuation — "Use local mock JSON data that resembles the expected
 * DeviceTelemetry structure... The purpose is to allow the monitoring UI, charts, live power flow,
 * battery SOC, PV power, grid power, load power and alerts to be developed and tested now"):
 * generates a plausible, clearly-labeled MOCK [DeviceTelemetry] snapshot shaped like what a real
 * manufacturer API would return, driven purely by wall-clock time so a monitoring screen polling
 * this repeatedly sees a "live"-looking day curve (sunrise → noon → sunset PV, a battery that
 * charges through the day and drains overnight, a household load with morning/evening bumps).
 *
 * This is deliberately NOT wired to this app's own [com.lumix.estimator.domain.simulation
 * .SimulationEngine] — reusing that real deterministic engine here would blur the line between
 * "this app's own verified engineering simulation" and "placeholder data standing in for a device
 * this app has no real connection to," which is exactly the confusion the spec's own "AI/mock data
 * must never be presented as real telemetry or secretly replace the deterministic engine" framing
 * (from the Phase 24 instructions this same round) warns against for the AI layer, and applies
 * here for the same reason. The curve shapes below are illustrative approximations for exercising
 * UI, not this app's verified PV/battery physics — [nominalCapacityKw] is a representative,
 * clearly-fictional system size per manufacturer, not a real customer's design.
 */
object MockMonitoringData {

    /** A distinct, clearly-fictional representative PV size per manufacturer, only so multiple mock devices look different in a device list — not tied to any real customer system. */
    private fun nominalCapacityKw(manufacturer: MonitoringManufacturer): Double = when (manufacturer) {
        MonitoringManufacturer.DEYE -> 8.0
        MonitoringManufacturer.LUXPOWER -> 6.0
        MonitoringManufacturer.GROWATT -> 5.0
        MonitoringManufacturer.SOLARMAN -> 10.0
        MonitoringManufacturer.SOLAR_OF_THINGS -> 6.5
    }

    private fun hourOfDay(atMillis: Long): Double {
        val cal = Calendar.getInstance()
        cal.timeInMillis = atMillis
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    }

    /** 0 outside 6am-6pm, a smooth bell shape peaking at solar noon otherwise — the same daylight-window assumption [com.lumix.estimator.domain.simulation.WeatherEngine] documents for its own curve. */
    private fun solarFraction(hour: Double): Double =
        if (hour in 6.0..18.0) sin(PI * (hour - 6.0) / 12.0).coerceIn(0.0, 1.0) else 0.0

    /** Household load: a low overnight baseline plus morning/evening bumps — illustrative only, not this app's real appliance-schedule model. */
    private fun loadFraction(hour: Double): Double {
        val morning = 0.5 * sin(PI * ((hour - 6.0).coerceIn(0.0, 3.0)) / 3.0).coerceIn(0.0, 1.0)
        val evening = 1.0 * sin(PI * ((hour - 17.0).coerceIn(0.0, 5.0)) / 5.0).coerceIn(0.0, 1.0)
        return (0.2 + morning + evening).coerceIn(0.15, 1.5)
    }

    /** Peaks in the early evening (after a day of solar charging), troughs just before sunrise. */
    private fun batterySocPercent(hour: Double): Float =
        (55.0 + 35.0 * cos(2.0 * PI * (hour - 17.0) / 24.0)).coerceIn(20.0, 96.0).toFloat()

    fun generate(manufacturer: MonitoringManufacturer, atMillis: Long = System.currentTimeMillis()): DeviceTelemetry {
        val hour = hourOfDay(atMillis)
        val capacityKw = nominalCapacityKw(manufacturer)
        val solar = solarFraction(hour)
        val pvPower = capacityKw * solar * 0.9 // 0.9: illustrative inverter/wiring derate, not a verified figure

        val loadPower = capacityKw * 0.35 * loadFraction(hour)
        val socPercent = batterySocPercent(hour)
        // Charging while there's solar surplus over load, discharging otherwise — sign convention
        // matches DeviceTelemetry.batteryPower's own doc (positive = charging).
        val batteryPower = (pvPower - loadPower).coerceIn(-capacityKw * 0.5, capacityKw * 0.5)
        val gridPower = max(0.0, loadPower - pvPower - max(0.0, -batteryPower))

        val pvVoltage = if (solar > 0.0) 340.0 + 40.0 * solar else 0.0
        val pvCurrent = if (pvVoltage > 0.0) (pvPower * 1000.0) / pvVoltage else 0.0
        val batteryVoltage = 48.0 + (socPercent / 100.0) * 5.0
        val temperature = 26.0 + 10.0 * solar

        return DeviceTelemetry(
            pvPower = pvPower,
            pvVoltage = pvVoltage,
            pvCurrent = pvCurrent,
            batterySoc = socPercent,
            batteryPower = batteryPower,
            batteryVoltage = batteryVoltage,
            loadPower = loadPower,
            gridPower = gridPower,
            // Illustrative running total for the day, not an integral of the exact curve above.
            energyToday = capacityKw * 4.0 * (hour / 24.0),
            // A mock lifetime counter (as if ~2.5 years installed) — populated here (unlike
            // SimulatedMonitoringProvider's honest null) because a real manufacturer API DOES
            // report this field, and mock data should resemble that real shape for UI development.
            energyTotal = capacityKw * 900.0,
            faultCode = null,
            temperature = temperature,
            timestamp = atMillis
        )
    }
}

/** A85: one threshold-derived alert for the monitoring UI's "Demonstrate alerts using mock data" requirement — illustrative rules, not a real device fault feed. */
data class MockMonitoringAlert(val severity: Severity, val message: String) {
    enum class Severity { INFO, WARNING, CRITICAL }
}

object MockMonitoringAlerts {
    /** Simple, illustrative threshold rules over one [DeviceTelemetry] snapshot — exercises the alerts UI without a real fault-code feed. */
    fun forTelemetry(telemetry: DeviceTelemetry): List<MockMonitoringAlert> = buildList {
        if (telemetry.batterySoc < 25f) {
            add(MockMonitoringAlert(MockMonitoringAlert.Severity.WARNING, "Battery SOC low (${telemetry.batterySoc.toInt()}%)"))
        }
        if (telemetry.gridPower > 0.0 && telemetry.batterySoc < 15f) {
            add(MockMonitoringAlert(MockMonitoringAlert.Severity.CRITICAL, "Battery critically low and importing from grid"))
        }
        if (telemetry.temperature > 45.0) {
            add(MockMonitoringAlert(MockMonitoringAlert.Severity.WARNING, "Elevated device temperature (${telemetry.temperature.toInt()}°C)"))
        }
    }
}
