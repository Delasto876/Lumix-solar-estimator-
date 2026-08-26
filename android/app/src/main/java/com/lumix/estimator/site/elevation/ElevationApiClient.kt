package com.lumix.estimator.site.elevation

import com.lumix.estimator.site.GeoPoint
import com.lumix.estimator.site.solarapi.GoogleSolarApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Site Survey / Solar Mapping round: fetches real ground elevation at one map point from Google's
 * Elevation API — see [ElevationResult]'s own doc for why this is a three-way result, not a
 * nullable. A plain interface so [com.lumix.estimator.site.SolarSiteViewModel] (and any test) can
 * substitute a fake without a real network dependency, mirroring [com.lumix.estimator.site.solarapi
 * .SolarApiClient]'s own testability split.
 */
interface ElevationApiClient {
    suspend fun elevationAt(point: GeoPoint): ElevationResult
}

/**
 * The real implementation — a single plain HTTPS GET against `maps.googleapis.com`'s Elevation API,
 * via the JDK's own [HttpURLConnection] for the same "don't pull in an HTTP client library for one
 * more endpoint" reason [com.lumix.estimator.site.solarapi.GoogleSolarApiClient] already documents.
 *
 * Deliberately reuses [GoogleSolarApiConfig]'s already-configured key rather than adding a fourth
 * `local.properties` entry: Elevation API is, like Solar API, a plain REST call this app makes
 * directly (not the Maps SDK's own manifest-key path — see [com.lumix.estimator.map.GoogleMapsConfig]'s
 * own doc for that distinction), and in practice both are enabled on the same Google Cloud
 * project/key. If a deployment genuinely needs Elevation restricted to a separate key from Solar
 * API, [GoogleSolarApiConfig] would need a second slot — not needed today.
 */
class GoogleElevationApiClient : ElevationApiClient {

    override suspend fun elevationAt(point: GeoPoint): ElevationResult {
        if (!GoogleSolarApiConfig.isConfigured) {
            return ElevationResult.Unavailable("No Google API key configured — add SOLAR_API_KEY to local.properties to enable elevation lookups.")
        }
        return withContext(Dispatchers.IO) {
            val url = buildUrl(point)
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    return@withContext ElevationResult.Unavailable("Elevation API request failed (HTTP $code)${errorBody?.let { ": $it" } ?: ""}.")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = ElevationApiResponseParser.parse(body)
                    ?: return@withContext ElevationResult.Unavailable("Elevation API returned an unexpected response format.")
                val (status, reading) = parsed
                when {
                    status == "OK" && reading != null -> ElevationResult.Available(reading)
                    status == "ZERO_RESULTS" -> ElevationResult.NoData("No elevation data for this location.")
                    else -> ElevationResult.Unavailable("Elevation API returned status $status.")
                }
            } catch (e: IOException) {
                ElevationResult.Unavailable("Could not reach the Elevation API — check your internet connection. (${e.message ?: e.javaClass.simpleName})")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun buildUrl(point: GeoPoint): URL {
        val key = URLEncoder.encode(GoogleSolarApiConfig.apiKey, "UTF-8")
        return URI(
            "https://maps.googleapis.com/maps/api/elevation/json" +
                "?locations=${point.latitude},${point.longitude}&key=$key"
        ).toURL()
    }
}
