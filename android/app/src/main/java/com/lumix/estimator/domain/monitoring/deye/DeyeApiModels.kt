package com.lumix.estimator.domain.monitoring.deye

import kotlinx.serialization.Serializable

/**
 * A149 (Deye integration round): the DeyeCloud Open Platform's wire shapes, as far as they could
 * be confirmed from outside an authenticated developer-portal session — `developer.deyecloud.com`
 * itself is blocked from this sandbox's network egress, so everything here is built from
 * DeyeCloudDevelopers' own public sample-code repo (`deye-openapi-client-sample-code` on GitHub)
 * and third-party client wrappers, not Deye's own field-by-field API reference. Two different
 * confidence levels below, kept explicit rather than blurred together:
 *
 * CONFIRMED (from DeyeCloudDevelopers' own `account/obtain_token.py` sample): the token request is
 * `POST {baseUrl}/account/token?appId={appId}` with a JSON body of `{appSecret, email, companyId,
 * password}`, `password` being the account's real password, SHA-256-hashed client-side before
 * sending — see [DeyeAuthClient] for where that happens.
 *
 * INFERRED, NOT CONFIRMED (from a third-party wrapper's README describing its own
 * `getCloudDevices`/`getLatestBySn` helper functions, not from Deye's own response schema): the
 * response envelope shape below (`code`/`msg`/`success`/`data`), the device-list endpoint path and
 * its fields, and the per-device telemetry `dataList` of `{key, value, unit}` metric entries. This
 * `{code,msg,success,data}` envelope is the near-universal convention across this exact family of
 * Chinese solar-monitoring cloud platforms (Growatt, Solarman, and others all use it), which is why
 * it's a reasonable starting guess — but it is a guess, not a documented contract. [RealDeyeProvider]
 * treats every parse failure as a real [com.lumix.estimator.domain.monitoring.MonitoringResult.Error]
 * /[com.lumix.estimator.domain.monitoring.DeviceListResult.Error] rather than silently returning
 * zeros, specifically so a wrong guess here fails loudly and diagnosably instead of quietly. THE
 * FIRST THING TO CHECK if a real account connects successfully (token obtained) but device/telemetry
 * calls keep erroring: log the raw response body and compare it against this file's assumptions,
 * then fix the wire models here — nothing else in the app needs to change (same "one place changes"
 * shape [com.lumix.estimator.domain.monitoring.MonitoringProviderRegistry] already documents).
 */
internal object DeyeApiModels {

    @Serializable
    data class TokenRequest(
        val appSecret: String,
        val email: String,
        val companyId: String,
        val password: String
    )

    /** INFERRED shape — see this file's own doc. `expiresIn` is assumed to be seconds, matching every other OAuth-style token response this app has seen a real spec for (Google's). */
    @Serializable
    data class TokenResponse(
        val code: String? = null,
        val msg: String? = null,
        val success: Boolean? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresIn: Long? = null
    )

    /** INFERRED shape for one entry in a device-list page. */
    @Serializable
    data class DeviceListEntry(
        val deviceSn: String? = null,
        val deviceType: String? = null,
        val name: String? = null,
        val alias: String? = null,
        val plantName: String? = null,
        val connectStatus: Int? = null
    )

    /** INFERRED shape — a paginated device list under the standard envelope. */
    @Serializable
    data class DeviceListResponse(
        val code: String? = null,
        val msg: String? = null,
        val success: Boolean? = null,
        val total: Int? = null,
        val deviceList: List<DeviceListEntry>? = null
    )

    /** INFERRED shape — one `{key, value, unit}` metric, per the third-party wrapper's own description of `getLatestBySn`'s output. */
    @Serializable
    data class TelemetryMetric(
        val key: String? = null,
        val value: String? = null,
        val unit: String? = null
    )

    /** INFERRED shape — one device's latest reading, keyed by its own serial number. */
    @Serializable
    data class DeviceLatestEntry(
        val deviceSn: String? = null,
        val dataList: List<TelemetryMetric>? = null
    )

    /** INFERRED shape — the envelope wrapping one or more [DeviceLatestEntry] results for a batch `getLatestBySn` call. */
    @Serializable
    data class DeviceLatestResponse(
        val code: String? = null,
        val msg: String? = null,
        val success: Boolean? = null,
        val deviceDataList: List<DeviceLatestEntry>? = null
    )
}
