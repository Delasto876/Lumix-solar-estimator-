package com.lumix.estimator.domain.monitoring

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A149 (Deye integration round): tests for the pieces that don't require a live network call —
 * [MonitoringCredentials.Deye]'s revised (account-login) shape, [FallbackMonitoringProvider]'s
 * mock/live routing logic (using small local fakes, not [com.lumix.estimator.domain.monitoring
 * .deye.RealDeyeProvider] itself — that class's actual HTTP calls are unverifiable against a real
 * DeyeCloud account from this test environment, exactly like this app's other real REST clients
 * [com.lumix.estimator.site.solarapi.GoogleSolarApiClient] etc. have no live-call unit test either),
 * and [listDevices] on the two providers that DO exist without a network dependency.
 *
 * [reset] always restores [MonitoringConfig]'s Deye slot to blank afterward — it's shared mutable
 * process-global state, and [SimulatedMonitoringProviderTest]'s own "every named manufacturer
 * defaults to a Mock provider when no credentials are configured" test depends on it being blank
 * regardless of which test class ran first in this JVM.
 */
class DeyeMonitoringTest {

    @After
    fun reset() {
        MonitoringConfig.updateDeye(MonitoringCredentials.Deye())
    }

    @Test
    fun `Deye credentials are configured only once appId, appSecret, email and password are all present`() {
        assertFalse(MonitoringCredentials.Deye().isConfigured)
        assertFalse(MonitoringCredentials.Deye(appId = "a", appSecret = "b", email = "c@d.com").isConfigured)
        assertTrue(MonitoringCredentials.Deye(appId = "a", appSecret = "b", email = "c@d.com", password = "pw").isConfigured)
    }

    @Test
    fun `MockMonitoringProvider lists exactly one clearly-labeled mock device`() {
        val result = runBlocking { MockMonitoringProvider(MonitoringManufacturer.DEYE).listDevices() }
        assertTrue(result is DeviceListResult.Available)
        val devices = (result as DeviceListResult.Available).devices
        assertEquals(1, devices.size)
        assertEquals(MonitoringManufacturer.DEYE, devices[0].manufacturer)
        assertTrue(devices[0].label.contains("mock", ignoreCase = true))
    }

    @Test
    fun `FallbackMonitoringProvider uses mock while offline regardless of what the real provider would return`() {
        // A distinctive faultCode the real provider "reports" — MockMonitoringData always leaves
        // faultCode null (see its own doc), so seeing it null back here is real proof mock
        // answered, not a tautological check against a fresh unrelated instance.
        val real = fakeProvider(fetchResult = MonitoringResult.Connected(sampleTelemetry().copy(faultCode = "REAL-PROVIDER-SENTINEL")))
        val mock = MockMonitoringProvider(MonitoringManufacturer.DEYE)
        val fallback = FallbackMonitoringProvider(real, mock, isOnline = { false })

        val result = runBlocking { fallback.fetchLatest("device-1") }
        assertTrue("offline should always defer to mock, not the real provider", result is MonitoringResult.Connected)
        assertEquals(null, (result as MonitoringResult.Connected).telemetry.faultCode)
    }

    @Test
    fun `FallbackMonitoringProvider falls back to mock only when the real provider reports NotConfigured`() {
        val real = fakeProvider(fetchResult = MonitoringResult.NotConfigured)
        val mock = MockMonitoringProvider(MonitoringManufacturer.DEYE)
        val fallback = FallbackMonitoringProvider(real, mock, isOnline = { true })

        val result = runBlocking { fallback.fetchLatest("device-1") }
        assertTrue(result is MonitoringResult.Connected)
    }

    @Test
    fun `FallbackMonitoringProvider surfaces a real error instead of silently masking it with mock data`() {
        val real = fakeProvider(fetchResult = MonitoringResult.Error("wrong password"))
        val mock = MockMonitoringProvider(MonitoringManufacturer.DEYE)
        val fallback = FallbackMonitoringProvider(real, mock, isOnline = { true })

        val result = runBlocking { fallback.fetchLatest("device-1") }
        assertTrue(result is MonitoringResult.Error)
        assertEquals("wrong password", (result as MonitoringResult.Error).message)
    }

    @Test
    fun `FallbackMonitoringProvider passes through a genuine real Connected result unchanged`() {
        val sentinel = MonitoringResult.Connected(sampleTelemetry())
        val real = fakeProvider(fetchResult = sentinel)
        val mock = MockMonitoringProvider(MonitoringManufacturer.DEYE)
        val fallback = FallbackMonitoringProvider(real, mock, isOnline = { true })

        val result = runBlocking { fallback.fetchLatest("device-1") }
        assertTrue(result === sentinel)
    }

    @Test
    fun `MonitoringProviderRegistry routes Deye to a real-manufacturer-tagged fallback provider that still answers with mock telemetry when unconfigured`() {
        val provider = MonitoringProviderRegistry.providerFor(MonitoringManufacturer.DEYE, isOnline = { true })
        assertEquals(MonitoringManufacturer.DEYE, provider.manufacturer)
        val listResult = runBlocking { provider.listDevices() }
        assertTrue(listResult is DeviceListResult.Available)
    }

    private fun sampleTelemetry() = DeviceTelemetry(
        pvPower = 1.23, pvVoltage = 340.0, pvCurrent = 3.6, batterySoc = 80f, batteryPower = 0.5,
        batteryVoltage = 51.2, loadPower = 0.9, gridPower = 0.0, energyToday = 4.2, energyTotal = null,
        faultCode = null, temperature = 30.0, timestamp = 1L
    )

    private fun fakeProvider(fetchResult: MonitoringResult): MonitoringProvider = object : MonitoringProvider {
        override val manufacturer: MonitoringManufacturer = MonitoringManufacturer.DEYE
        override suspend fun fetchLatest(deviceId: String): MonitoringResult = fetchResult
        override suspend fun listDevices(): DeviceListResult = DeviceListResult.NotConfigured
    }
}
