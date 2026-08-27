package com.lumix.estimator.domain.monitoring

import com.lumix.estimator.domain.simulation.ApplianceState
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.TechnicalModel
import com.lumix.estimator.domain.simulation.TechnicalReadout

/**
 * A83 (spec Phase 22, original §63 — "FUTURE MONITORING"): the one [MonitoringProvider]
 * implementation that actually exists today — not a real device feed, a [DeviceTelemetry]
 * snapshot of THIS APP'S OWN already-computed [TechnicalReadout] at one simulated instant. Proves
 * the normalized schema is genuinely populated from real internal engineering data wherever this
 * app tracks the equivalent figure (PV/battery voltage and current come from the real per-MPPT
 * electrical model, A53's own work — not invented for this round), and left null exactly where it
 * doesn't (see [DeviceTelemetry.energyTotal]/[DeviceTelemetry.faultCode]'s own docs).
 * [manufacturer] is null — this isn't tied to any real manufacturer's protocol.
 */
class SimulatedMonitoringProvider(
    private val config: SimSystemConfig,
    private val timeline: List<SimFrame>,
    private val frame: SimFrame,
    private val appliances: Map<SimApplianceType, ApplianceState> = emptyMap(),
    private val gridServiceAmps: Double,
    private val dayType: DayType = DayType.WEEKDAY
) : MonitoringProvider {

    override val manufacturer: MonitoringManufacturer? = null

    override suspend fun fetchLatest(deviceId: String): MonitoringResult {
        val readout = TechnicalModel.compute(frame, config, timeline, appliances, gridServiceAmps, dayType)
        return MonitoringResult.Connected(toDeviceTelemetry(readout, frame))
    }

    /** A149: this provider only ever represents the one quote/system it was constructed for — a single fixed-ID entry, not a real multi-device directory. */
    override suspend fun listDevices(): DeviceListResult = DeviceListResult.Available(
        listOf(DeviceSummary(id = THIS_SYSTEM_DEVICE_ID, label = "This system (simulated)", manufacturer = null))
    )

    companion object {
        const val THIS_SYSTEM_DEVICE_ID = "this-system"

        /** Pure mapping, exposed separately so it's directly unit-testable without constructing a whole provider. */
        fun toDeviceTelemetry(
            readout: TechnicalReadout,
            frame: SimFrame,
            timestampMillis: Long = System.currentTimeMillis()
        ): DeviceTelemetry = DeviceTelemetry(
            pvPower = readout.pvPowerKw,
            pvVoltage = readout.pvVoltage,
            pvCurrent = readout.pvCurrent,
            batterySoc = readout.batterySocPercent,
            batteryPower = frame.batteryPowerKw,
            batteryVoltage = readout.batteryVoltage,
            loadPower = frame.houseLoadKw,
            gridPower = frame.gridPowerKw,
            energyToday = readout.energyTodayKwh,
            energyTotal = null,
            faultCode = null,
            temperature = readout.cellTempC,
            timestamp = timestampMillis
        )
    }
}
