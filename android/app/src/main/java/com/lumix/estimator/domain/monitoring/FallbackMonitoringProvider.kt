package com.lumix.estimator.domain.monitoring

/**
 * A149 (Deye integration round, satisfying "app works with simulated/demo data by default...
 * automatically switches to live Deye data once the device has an internet connection and the API
 * is reachable"): wraps a real manufacturer [MonitoringProvider] and a [MockMonitoringProvider]
 * fallback, deciding which one answers EACH call — not a single choice made once at app start —
 * so a device that comes online mid-session (or a Deye account connected after the app was already
 * running) starts showing live data on its very next poll, with no restart needed.
 *
 * The fallback rule is deliberately narrow: [mock] only answers when [real] genuinely can't be
 * reached at all — no credentials configured ([MonitoringResult.NotConfigured]) or the device has
 * no internet connection right now (per [isOnline]). A real, configured, online call that FAILS
 * (wrong password, DeyeCloud down, an unparseable response) is NOT quietly replaced by mock data —
 * it surfaces as a real [MonitoringResult.Error]/[DeviceListResult.Error], because that's an
 * actionable problem (reconnect the account, check DeyeCloud's own status) the installer needs to
 * see, not something this app should paper over with a synthetic curve that looks fine. Silently
 * showing fake "everything's normal" data over a real auth failure would be a worse, more
 * deceptive outcome than just showing the error — the same "never fabricate, never mislead"
 * reasoning this codebase already applies everywhere else (mock monitoring, AI explanations,
 * equipment specs).
 */
class FallbackMonitoringProvider(
    private val real: MonitoringProvider,
    private val mock: MonitoringProvider,
    private val isOnline: () -> Boolean
) : MonitoringProvider {

    override val manufacturer: MonitoringManufacturer? = real.manufacturer

    override suspend fun fetchLatest(deviceId: String): MonitoringResult {
        if (!isOnline()) return mock.fetchLatest(deviceId)
        return when (val result = real.fetchLatest(deviceId)) {
            is MonitoringResult.NotConfigured -> mock.fetchLatest(deviceId)
            is MonitoringResult.Connected, is MonitoringResult.Error -> result
        }
    }

    override suspend fun listDevices(): DeviceListResult {
        if (!isOnline()) return mock.listDevices()
        return when (val result = real.listDevices()) {
            is DeviceListResult.NotConfigured -> mock.listDevices()
            is DeviceListResult.Available, is DeviceListResult.Error -> result
        }
    }
}
