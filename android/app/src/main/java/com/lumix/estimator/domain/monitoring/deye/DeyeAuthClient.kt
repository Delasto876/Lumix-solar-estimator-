package com.lumix.estimator.domain.monitoring.deye

import com.lumix.estimator.domain.monitoring.MonitoringCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Outcome of a [DeyeAuthClient.login] attempt — see [DeyeAuthClient]'s own doc for why this is two-way, not a nullable/thrown-exception pair. */
sealed class DeyeAuthResult {
    data class Authenticated(val accessToken: String, val expiresAtMillis: Long?) : DeyeAuthResult()
    data class Failed(val reason: String) : DeyeAuthResult()
}

/**
 * A149 (Deye integration round): the one CONFIRMED piece of the whole Deye integration — the login
 * request itself, built exactly as DeyeCloudDevelopers' own `clientcode/account/obtain_token.py`
 * sample does it (see [DeyeApiModels]'s own doc for the confidence split across this package):
 * `POST {baseUrl}/account/token?appId={appId}` with a JSON body of `{appSecret, email, companyId,
 * password}`, where `password` is the account's real password, SHA-256-hashed to lowercase hex
 * before it ever leaves the device — this app never sends (or persists) the plaintext password,
 * matching [MonitoringCredentials.Deye]'s own doc for why the raw password isn't stored either.
 *
 * The RESPONSE shape ([DeyeApiModels.TokenResponse]) is inferred, not confirmed — this client
 * treats a missing `accessToken` field as a real login failure ([DeyeAuthResult.Failed]) rather
 * than crashing or fabricating a token, so a wrong guess about the response shape surfaces as an
 * honest "couldn't log in" message instead of silently behaving as if authentication worked.
 */
class DeyeAuthClient(private val baseUrl: String = DEFAULT_BASE_URL) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(credentials: MonitoringCredentials.Deye): DeyeAuthResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val requestBody = json.encodeToString(
                DeyeApiModels.TokenRequest.serializer(),
                DeyeApiModels.TokenRequest(
                    appSecret = credentials.appSecret,
                    email = credentials.email,
                    companyId = credentials.companyId,
                    password = sha256Hex(credentials.password)
                )
            )
            connection = (buildTokenUrl(credentials.appId).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext DeyeAuthResult.Failed("DeyeCloud login failed (HTTP $code)${errorBody?.let { ": $it" } ?: ""}.")
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = runCatching { json.decodeFromString(DeyeApiModels.TokenResponse.serializer(), responseBody) }.getOrNull()
                ?: return@withContext DeyeAuthResult.Failed("DeyeCloud login returned an unexpected response format.")
            val token = parsed.accessToken
            if (token.isNullOrBlank()) {
                return@withContext DeyeAuthResult.Failed(parsed.msg?.takeIf { it.isNotBlank() } ?: "DeyeCloud login did not return an access token.")
            }
            val expiresAtMillis = parsed.expiresIn?.let { System.currentTimeMillis() + it * 1000L }
            DeyeAuthResult.Authenticated(token, expiresAtMillis)
        } catch (e: IOException) {
            DeyeAuthResult.Failed("Could not reach DeyeCloud — check your internet connection. (${e.message ?: e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildTokenUrl(appId: String): URL {
        val encodedAppId = URLEncoder.encode(appId, "UTF-8")
        return URI("$baseUrl/account/token?appId=$encodedAppId").toURL()
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        /**
         * DeyeCloudDevelopers' own sample code defaults to this EU region endpoint. DeyeCloud's
         * region-routing scheme for non-EU accounts (e.g. a Jamaica/Caribbean installer's account)
         * was NOT confirmed from anywhere reachable in this sandbox — the installer should verify
         * their own account's correct base URL against developer.deyecloud.com once they have
         * portal access, and this default should be treated as a starting guess, not a settled fact.
         */
        const val DEFAULT_BASE_URL = "https://eu1-developer.deyecloud.com/v1.0"
    }
}
