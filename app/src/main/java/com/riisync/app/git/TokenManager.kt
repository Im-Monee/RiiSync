/**
 * Secure Token Storage for RiiSync.
 * This file handles the encryption and storage of sensitive GitHub credentials like
 * personal access tokens and usernames using Android's EncryptedSharedPreferences.
 */
package com.riisync.app.git

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import java.security.KeyStore

/**
 * Manager class for secure storage of credentials.
 * @param context The application context used to initialize SharedPreferences.
 */
class TokenManager(context: Context) {
    companion object {
        private const val KEY_ALIAS = "riisync_master_key"
    }

    init { ensureKeyExists() }

    /**
     * Ensures an AES key with the configured alias exists in the AndroidKeyStore.
     * Creates one using javax.crypto.KeyGenerator when missing.
     */
    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private val prefs = EncryptedSharedPreferences.create(
            "secure_tokens",
            KEY_ALIAS,
            context,
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
