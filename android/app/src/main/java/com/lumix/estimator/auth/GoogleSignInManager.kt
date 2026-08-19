package com.lumix.estimator.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom

/** On-device identity read straight off a signed Google ID token's claims — nothing more. */
data class SignedInGoogleUser(
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

sealed interface GoogleSignInResult {
    data class Success(val user: SignedInGoogleUser) : GoogleSignInResult
    /** The account picker was dismissed without a selection — not an error. */
    data object Cancelled : GoogleSignInResult
    data class Failed(val message: String) : GoogleSignInResult
}

/**
 * 2026-08-19 ("do this google sign in/OAuth" — confirmed scope: identity-capture only, a button
 * in Settings, nothing gated, no backend): wraps Android's Credential Manager "Sign in with
 * Google" flow (`androidx.credentials` + `com.google.android.libraries.identity.googleid`) — the
 * current Google-recommended API, not the older `com.google.android.gms.auth.api.signin
 * .GoogleSignInClient`, which Google has been deprecating in favor of this one. The returned
 * [SignedInGoogleUser] is parsed straight off the signed ID token's own claims on-device; nothing
 * is sent to a Lumix backend (there isn't one) and no server-side verification happens — that's
 * fine for "prefill a name/email field," but this identity must NOT be treated as a verified/
 * authenticated session for anything security-sensitive without adding real backend token
 * verification first.
 *
 * **Requires an Activity [Context]**, unlike e.g. [com.lumix.estimator.location
 * .DeviceLocationManager] (which deliberately downgrades to `applicationContext` since it needs
 * no UI) — Credential Manager has to host the account-picker bottom sheet on top of an Activity,
 * so [SettingsScreen][com.lumix.estimator.ui.settings.SettingsScreen] must pass
 * `LocalContext.current` here as-is, not `.applicationContext`.
 */
class GoogleSignInManager(private val context: Context) {

    suspend fun signIn(): GoogleSignInResult {
        val serverClientId = GoogleIdentityConfig.serverClientIdOrNull
            ?: return GoogleSignInResult.Failed("Google Sign-In isn't configured yet — add GOOGLE_WEB_CLIENT_ID to local.properties.")

        // Random per-attempt nonce — not verified anywhere in this identity-only flow (there's no
        // backend to check it against), but costs nothing and keeps the door open for adding real
        // server-side verification later without having to touch this call site again.
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(
                    SignedInGoogleUser(
                        displayName = googleIdTokenCredential.displayName,
                        email = googleIdTokenCredential.id,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    )
                )
            } else {
                GoogleSignInResult.Failed("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed(e.message ?: "Sign-in failed.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed("Couldn't read the Google account's identity token.")
        }
    }

    /**
     * Clears Credential Manager's own cached sign-in state, not just [SettingsRepository]'s
     * locally-stored name/email — without this, Credential Manager can silently hand back the
     * same account on the next [signIn] call rather than showing the picker again. Best-effort:
     * failures are swallowed since this is a courtesy cleanup, not something the caller needs to
     * react to (the local identity is cleared by the caller regardless of this call's outcome).
     */
    suspend fun signOut() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            // Best-effort — see doc above.
        }
    }
}
