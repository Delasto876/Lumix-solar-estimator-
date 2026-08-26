package com.lumix.estimator.site.solarapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Site Survey / Solar Mapping round: fetches a building's real Google Solar API "Building Insights"
 * result for one coordinate — see [SolarApiResult]'s own doc for why this is a three-way result
 * rather than a nullable, and [SolarApiRoofSegment]'s own doc for what each returned field actually
 * represents. A plain interface so [com.lumix.estimator.site.SolarSiteViewModel] (and any test) can
 * substitute a fake without a real network dependency, mirroring [com.lumix.estimator.domain
 * .monitoring.MonitoringProvider]'s own real-vs-mock split for the exact same testability reason.
 */
interface SolarApiClient {
    suspend fun fetchBuildingInsights(latitude: Double, longitude: Double): SolarApiResult
}

/**
 * The real implementation — a single plain HTTPS GET against `solar.googleapis.com`'s
 * `buildingInsights:findClosest` endpoint (spec's own "Use official current Google documentation
 * rather than deprecated APIs"; this is the current, documented Solar API endpoint, not a
 * predecessor). Deliberately uses the JDK's own [HttpURLConnection] rather than adding a new HTTP
 * client dependency (OkHttp/Retrofit/Ktor) — this app makes exactly one kind of outbound REST call
 * so far, and the spec's own "use the minimum required Google APIs" principle extends naturally to
 * not pulling in a whole client library for one endpoint.
 *
 * Every failure path below resolves to [SolarApiResult.NoCoverage] or [SolarApiResult.Unavailable]
 * — never a thrown exception the caller has to separately guard against, and never a fabricated
 * substitute result (spec's own "Never fabricate roof geometry, shading or solar data").
 */
class GoogleSolarApiClient : SolarApiClient {

    override suspend fun fetchBuildingInsights(latitude: Double, longitude: Double): SolarApiResult {
        if (!GoogleSolarApiConfig.isConfigured) {
            return SolarApiResult.Unavailable("No Google Solar API key configured — add SOLAR_API_KEY to local.properties to enable automatic roof detection.")
        }
        return withContext(Dispatchers.IO) {
            val url = buildUrl(latitude, longitude)
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = connection.responseCode
                when (code) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val insights = SolarApiResponseParser.parse(body)
                        if (insights != null) {
                            SolarApiResult.Available(insights)
                        } else {
                            SolarApiResult.Unavailable("Solar API returned an unexpected response format.")
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        SolarApiResult.NoCoverage("Google Solar API has no building data for this location — trace the roof manually instead.")
                    }
                    else -> {
                        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        SolarApiResult.Unavailable("Solar API request failed (HTTP $code)${errorBody?.let { ": $it" } ?: ""}.")
                    }
                }
            } catch (e: IOException) {
                SolarApiResult.Unavailable("Could not reach the Solar API — check your internet connection. (${e.message ?: e.javaClass.simpleName})")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun buildUrl(latitude: Double, longitude: Double): URL {
        val key = URLEncoder.encode(GoogleSolarApiConfig.apiKey, "UTF-8")
        return URI(
            "https://solar.googleapis.com/v1/buildingInsights:findClosest" +
                "?location.latitude=$latitude&location.longitude=$longitude" +
                "&requiredQuality=HIGH&key=$key"
        ).toURL()
    }
}
