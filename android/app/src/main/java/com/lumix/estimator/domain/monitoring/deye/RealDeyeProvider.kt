package com.lumix.estimator.domain.monitoring.deye

import com.lumix.estimator.domain.monitoring.DeviceListResult
import com.lumix.estimator.domain.monitoring.DeviceSummary
import com.lumix.estimator.domain.monitoring.DeviceTelemetry
import com.lumix.estimator.domain.monitoring.MonitoringCredentials
import com.lumix.estimator.domain.monitoring.MonitoringManufacturer
import com.lumix.estimator.domain.monitoring.MonitoringProvider
import com.lumix.estimator.domain.monitoring.MonitoringResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * A149 (Deye integration round): the first real (non-mock) [MonitoringProvider] this app has ever
 * had — see [DeyeApiModels]'s own doc for exactly which parts of the wire format are CONFIRMED
 * (the login request) vs. INFERRED from third-party sample code (everything else: response shapes,
 * the device-list/telemetry endpoint paths, and — the single least-confirmed piece — the raw
 * telemetry metric key names in [METRIC_KEY_CANDIDATES] below). Every failure path here (a bad
 * login, an unreachable endpoint, an unparseable response, a metric this app couldn't map) resolves
 * to a real [MonitoringResult.Error]/[DeviceListResult.Error] — never a silently zeroed or
 * fabricated telemetry snapshot, matching every other real API client in this codebase (Google
 * Solar/Elevation/Street View) and the domain's own "never fabricate" discipline.
 *
 * Kept pure Kotlin (no `android.*` import) like every other real REST client in this app, via the
 * JDK's own [HttpURLConnection] — this app's now-established "don't pull in an HTTP client library
 * for one more endpoint" reasoning applies here too.
 *
 * [credentials.accessToken] lets a caller seed this provider with an already-obtained access
 * token — the restart path: [MonitoringCredentials.Deye]'s own doc explains why this app never
 * persists the account password, so a provider restored after a process restart typically has
 * [credentials] with a blank [MonitoringCredentials.Deye.password] but a real persisted
 * [MonitoringCredentials.Deye.accessToken]. Without it, such a provider could never do anything
 * (no password to log in with, no token to use) — with it, the persisted token keeps working
 * until it actually expires, at which point [currentToken] correctly falls back to attempting a
 * real login (which then fails cleanly with a real [DeyeAuthResult.Failed] since [credentials] has
 * no password), surfacing as a real error the installer can act on (reconnect in Settings) rather
 * than a silent dead end.
 */
class RealDeyeProvider(
    private val credentials: MonitoringCredentials.Deye,
    private val authClient: DeyeAuthClient = DeyeAuthClient(),
    private val baseUrl: String = DeyeAuthClient.DEFAULT_BASE_URL
) : MonitoringProvider {

    override val manufacturer: MonitoringManufacturer = MonitoringManufacturer.DEYE

    private val json = Json { ignoreUnknownKeys = true }

    /** Seeded from [MonitoringCredentials.Deye.accessToken] (a restored, previously-obtained token) — otherwise in-memory only, so a fresh [RealDeyeProvider] with no seed always re-authenticates rather than trusting nothing. */
    @Volatile private var cachedToken: DeyeAuthResult.Authenticated? =
        credentials.accessToken?.let { DeyeAuthResult.Authenticated(it, credentials.tokenExpiresAtMillis) }

    /** Usable if a fresh login is possible ([credentials] is fully configured) OR a still-valid token was seeded in — see the class doc for why these differ after a restart. */
    private val isUsable: Boolean
        get() = credentials.isConfigured || cachedToken != null

    private suspend fun currentToken(): DeyeAuthResult {
        val cached = cachedToken
        if (cached != null && (cached.expiresAtMillis == null || cached.expiresAtMillis > System.currentTimeMillis() + TOKEN_EXPIRY_SAFETY_MARGIN_MILLIS)) {
            return cached
        }
        if (!credentials.isConfigured) {
            return DeyeAuthResult.Failed("DeyeCloud session expired — reconnect your account in Settings.")
        }
        val result = authClient.login(credentials)
        if (result is DeyeAuthResult.Authenticated) cachedToken = result
        return result
    }

    override suspend fun listDevices(): DeviceListResult {
        if (!isUsable) return DeviceListResult.NotConfigured
        return withContext(Dispatchers.IO) {
            when (val auth = currentToken()) {
                is DeyeAuthResult.Failed -> DeviceListResult.Error(auth.reason)
                is DeyeAuthResult.Authenticated -> fetchDeviceList(auth)
            }
        }
    }

    private fun fetchDeviceList(auth: DeyeAuthResult.Authenticated): DeviceListResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (buildDeviceListUrl().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "bearer ${auth.accessToken}")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                return DeviceListResult.Error("DeyeCloud device list request failed (HTTP $code)${errorBody?.let { ": $it" } ?: ""}.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = runCatching { json.decodeFromString(DeyeApiModels.DeviceListResponse.serializer(), body) }.getOrNull()
                ?: return DeviceListResult.Error("DeyeCloud device list returned an unexpected response format — see RealDeyeProvider's own doc, this endpoint's wire shape is unconfirmed.")
            val devices = parsed.deviceList.orEmpty().mapNotNull { entry ->
                val sn = entry.deviceSn ?: return@mapNotNull null
                DeviceSummary(
                    id = sn,
                    label = entry.alias?.takeIf { it.isNotBlank() } ?: entry.name?.takeIf { it.isNotBlank() } ?: sn,
                    manufacturer = MonitoringManufacturer.DEYE,
                    plantName = entry.plantName
                )
            }
            DeviceListResult.Available(devices)
        } catch (e: IOException) {
            DeviceListResult.Error("Could not reach DeyeCloud — check your internet connection. (${e.message ?: e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun fetchLatest(deviceId: String): MonitoringResult {
        if (!isUsable) return MonitoringResult.NotConfigured
        return withContext(Dispatchers.IO) {
            when (val auth = currentToken()) {
                is DeyeAuthResult.Failed -> MonitoringResult.Error(auth.reason)
                is DeyeAuthResult.Authenticated -> fetchTelemetry(auth, deviceId)
            }
        }
    }

    private fun fetchTelemetry(auth: DeyeAuthResult.Authenticated, deviceId: String): MonitoringResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (buildLatestUrl(deviceId).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "bearer ${auth.accessToken}")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                return MonitoringResult.Error("DeyeCloud telemetry request failed (HTTP $code)${errorBody?.let { ": $it" } ?: ""}.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = runCatching { json.decodeFromString(DeyeApiModels.DeviceLatestResponse.serializer(), body) }.getOrNull()
                ?: return MonitoringResult.Error("DeyeCloud telemetry returned an unexpected response format — see RealDeyeProvider's own doc, this endpoint's wire shape is unconfirmed.")
            val entry = parsed.deviceDataList.orEmpty().firstOrNull { it.deviceSn == deviceId }
                ?: return MonitoringResult.Error("DeyeCloud returned no telemetry for device $deviceId.")
            val telemetry = toDeviceTelemetry(entry)
                ?: return MonitoringResult.Error("DeyeCloud's telemetry response didn't include the fields this app expects — see RealDeyeProvider.METRIC_KEY_CANDIDATES, the raw metric key names are unconfirmed and may need adjusting to match this account's real response.")
            MonitoringResult.Connected(telemetry)
        } catch (e: IOException) {
            MonitoringResult.Error("Could not reach DeyeCloud — check your internet connection. (${e.message ?: e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Maps DeyeCloud's own `{key, value, unit}` metric list onto [DeviceTelemetry] — the single
     * least-confirmed part of this whole file (see the class doc). Every field here is required;
     * a metric this app can't find under any of its candidate key names means [fetchTelemetry]
     * reports a real [MonitoringResult.Error] naming this function, rather than silently
     * defaulting a missing reading to zero (which would look like a real "0 kW right now" instead
     * of "this app doesn't know DeyeCloud's real key name for this field yet").
     */
    private fun toDeviceTelemetry(entry: DeyeApiModels.DeviceLatestEntry): DeviceTelemetry? {
        val metrics: Map<String, Double> = entry.dataList.orEmpty()
            .mapNotNull { m -> val k = m.key ?: return@mapNotNull null; val v = m.value?.toDoubleOrNull() ?: return@mapNotNull null; k to v }
            .toMap()
        fun find(vararg candidates: String): Double? = candidates.firstNotNullOfOrNull { metrics[it] }

        val pvPower = find(*METRIC_KEY_CANDIDATES.getValue("pvPower")) ?: return null
        val batterySoc = find(*METRIC_KEY_CANDIDATES.getValue("batterySoc")) ?: return null
        val batteryPower = find(*METRIC_KEY_CANDIDATES.getValue("batteryPower")) ?: 0.0
        val loadPower = find(*METRIC_KEY_CANDIDATES.getValue("loadPower")) ?: return null
        val gridPower = find(*METRIC_KEY_CANDIDATES.getValue("gridPower")) ?: 0.0
        val temperature = find(*METRIC_KEY_CANDIDATES.getValue("temperature")) ?: return null

        return DeviceTelemetry(
            pvPower = pvPower,
            pvVoltage = find(*METRIC_KEY_CANDIDATES.getValue("pvVoltage")) ?: 0.0,
            pvCurrent = find(*METRIC_KEY_CANDIDATES.getValue("pvCurrent")) ?: 0.0,
            batterySoc = batterySoc.toFloat(),
            batteryPower = batteryPower,
            batteryVoltage = find(*METRIC_KEY_CANDIDATES.getValue("batteryVoltage")) ?: 0.0,
            loadPower = loadPower,
            gridPower = gridPower,
            energyToday = find(*METRIC_KEY_CANDIDATES.getValue("energyToday")) ?: 0.0,
            energyTotal = find(*METRIC_KEY_CANDIDATES.getValue("energyTotal")),
            faultCode = null,
            temperature = temperature,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun buildDeviceListUrl(): URL = URI("$baseUrl/device/list?page=1&size=100").toURL()

    private fun buildLatestUrl(deviceId: String): URL {
        val encodedSn = URLEncoder.encode(deviceId, "UTF-8")
        return URI("$baseUrl/device/latest?deviceSn=$encodedSn").toURL()
    }

    companion object {
        private const val TOKEN_EXPIRY_SAFETY_MARGIN_MILLIS = 60_000L

        /**
         * Every candidate raw key name this app will check for each [DeviceTelemetry] field,
         * tried in order — a defensive spread across the plausible naming conventions this family
         * of API tends to use (camelCase, snake_case, and a human-readable label seen in one
         * third-party summary), NOT a confirmed list from Deye's own docs. Update this table once
         * a real account's raw response is available to inspect — nothing else in
         * [toDeviceTelemetry] needs to change when these are corrected.
         */
        private val METRIC_KEY_CANDIDATES: Map<String, Array<String>> = mapOf(
            "pvPower" to arrayOf("pvPower", "pv_power", "PV Power"),
            "pvVoltage" to arrayOf("pvVoltage", "pv_voltage", "PV Voltage"),
            "pvCurrent" to arrayOf("pvCurrent", "pv_current", "PV Current"),
            "batterySoc" to arrayOf("batterySoc", "batterySOC", "battery_soc", "batteryCharge", "Battery SOC"),
            "batteryPower" to arrayOf("batteryPower", "battery_power", "Battery Power"),
            "batteryVoltage" to arrayOf("batteryVoltage", "battery_voltage", "Battery Voltage"),
            "loadPower" to arrayOf("loadPower", "load_power", "Load Power"),
            "gridPower" to arrayOf("gridPower", "grid_power", "Grid Power"),
            "energyToday" to arrayOf("energyToday", "todayEnergy", "generationToday", "Today's Generation"),
            "energyTotal" to arrayOf("energyTotal", "totalEnergy", "totalGeneration", "Total Generation"),
            "temperature" to arrayOf("temperature", "deviceTemperature", "Temperature")
        )
    }
}
