package com.xavadigital.mileagetracker.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * OAuth authorization for the Sheets API via Play Services. The consent grant is
 * remembered by Google Play, so after the one-time interactive approval in Settings,
 * [getAccessToken] silently returns fresh tokens for the background sync worker.
 */
object GoogleAuth {

    private const val SPREADSHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

    private fun request(): AuthorizationRequest = AuthorizationRequest.Builder()
        .setRequestedScopes(listOf(Scope(SPREADSHEETS_SCOPE)))
        .build()

    /**
     * Full authorization result — may carry a resolution PendingIntent that the
     * Settings screen must launch for the one-time consent dialog.
     */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    /** Silent token fetch; null when user consent is (still) required. */
    suspend fun getAccessToken(context: Context): String? = try {
        val result = authorize(context)
        if (result.hasResolution()) null else result.accessToken
    } catch (_: Exception) {
        null
    }
}
