/**
 * Secure Token Storage for RiiSync.
 * This file handles the encryption and storage of sensitive GitHub credentials like
 * personal access tokens and usernames using Android's EncryptedSharedPreferences.
 */
package com.riisync.app.git

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manager class for secure storage of credentials.
 * @param context The application context used to initialize SharedPreferences.
 */
class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Persists the GitHub personal access token securely.
     */
    fun saveToken(token: String) {
        prefs.edit().putString("github_token", token).apply()
    }

    /**
     * Retrieves the stored GitHub personal access token.
     */
    fun getToken(): String? {
        return prefs.getString("github_token", null)
    }

    /**
     * Persists the GitHub username securely.
     */
    fun saveUsername(username: String) {
        prefs.edit().putString("github_username", username).apply()
    }

    /**
     * Retrieves the stored GitHub username.
     */
    fun getUsername(): String? {
        return prefs.getString("github_username", null)
    }
}
