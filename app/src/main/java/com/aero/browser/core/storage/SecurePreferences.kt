package com.aero.browser.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Sensitive key-value storage backed by `EncryptedSharedPreferences`
 * (Tink AES-256 GCM/SIV, master key in the Android Keystore).
 * Used for: privacy toggles, default search engine, session state.
 *
 * NOTE: `androidx.security:security-crypto` is deprecated by AndroidX. This
 * compatibility wrapper uses it for the current phase, fails closed on any
 * initialization error, and is isolated so migration to a reviewed Keystore
 * plus DataStore implementation can replace it centrally.
 */
class SecurePreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = try {
        create(context)
    } catch (t: Throwable) {
        // Fail closed: never fall back to unencrypted preferences.
        throw IllegalStateException("EncryptedSharedPreferences unavailable", t)
    }

    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)
    fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)
    fun getStringEntries(prefix: String): Map<String, String> = prefs.all
        .filterKeys { it.startsWith(prefix) }
        .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
        .toMap()
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_FILE = "aero_secure_prefs"

        @Volatile private var instance: SecurePreferences? = null

        fun get(context: Context): SecurePreferences =
            instance ?: synchronized(this) {
                instance ?: SecurePreferences(context.applicationContext).also { instance = it }
            }

        @Suppress("DEPRECATION")
        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
