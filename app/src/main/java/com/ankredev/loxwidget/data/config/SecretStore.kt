package com.ankredev.loxwidget.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Verschlüsselte Ablage für sensible Werte (Loxone-Passwort, Token).
 * Nutzt Android Keystore via EncryptedSharedPreferences.
 */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "loxone_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var tokenValidUntil: Long
        get() = prefs.getLong(KEY_TOKEN_VALID, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_VALID, value).apply()

    fun clearToken() = prefs.edit()
        .remove(KEY_TOKEN)
        .remove(KEY_TOKEN_VALID)
        .apply()

    private companion object {
        const val KEY_PASSWORD = "password"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_VALID = "token_valid_until"
    }
}
