package com.lumix.estimator.domain.monitoring

/**
 * A85 (Phase 23 continuation): local development/testing stand-in for a real manufacturer feed —
 * "Do NOT make real API calls. Do NOT require API keys." Returns [MockMonitoringData.generate]'s
 * clearly-labeled synthetic snapshot, always [MonitoringResult.Connected] (never [MonitoringResult
 * .NotConfigured]), so a monitoring dashboard built against this provider sees the same shape of
 * result it would from a real one. [MonitoringProviderRegistry] is the one place this ever gets
 * swapped for a real provider — see that object's own doc.
 */
class MockMonitoringProvider(override val manufacturer: MonitoringManufacturer) : MonitoringProvider {
    override suspend fun fetchLatest(deviceId: String): MonitoringResult =
        MonitoringResult.Connected(MockMonitoringData.generate(manufacturer))

    /** A149: one clearly-labeled mock device per manufacturer — [fetchLatest] already ignores [deviceId] and returns the same generated curve regardless, so [MOCK_DEVICE_ID] is a fixed, documented placeholder rather than a real serial number. */
    override suspend fun listDevices(): DeviceListResult = DeviceListResult.Available(
        listOf(DeviceSummary(id = MOCK_DEVICE_ID, label = "${manufacturer.label} Demo Inverter (mock)", manufacturer = manufacturer))
    )

    companion object {
        const val MOCK_DEVICE_ID = "mock"
    }
}
