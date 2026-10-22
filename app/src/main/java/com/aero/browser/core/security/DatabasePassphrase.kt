package com.aero.browser.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Database at-rest encryption key management.
 *
 * Design (mirrors the desktop vault's "random data key wrapped by OS key"
 * pattern — desktop used DPAPI, Android uses Android Keystore):
 *
 *  - A random 32-byte SQLCipher passphrase is generated once on first run.
 *  - An AES-256-GCM key is generated in the **Android Keystore**
 *    (non-exportable, StrongBox on supported hardware).
 *  - The passphrase bytes are encrypted with that Keystore key and stored as
 *    base64 in an ordinary shared preferences file. Deleting that file (or
 *    the Keystore key) makes the DB permanently unreadable — intentional:
 *    the Fire Button's "clear all data" wipes both.
 *
 * No passphrase bytes are written to logs or returned to callers beyond the
 * unwrap operation itself.
 */
object DatabasePassphrase {

    private const val PREFS_FILE = "aero.dbkey"       // name only; not sensitive
    private const val KEY_CIPHERTEXT = "db_ciphertext"
    private const val KEYSTORE_ALIAS = "aero_db_master"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /** Returns the unwrapped passphrase, creating + persisting it on first run. */
    fun getOrCreate(context: Context): ByteArray {
        synchronized(this) {
            val prefs = prefs(context)
            prefs.getString(KEY_CIPHERTEXT, null)?.let { stored ->
                return unwrap(stored)
            }

            // First run: generate and persist a fresh wrapped passphrase.
            val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            val wrapped = wrap(fresh)
            check(prefs.edit().putString(KEY_CIPHERTEXT, wrapped).commit()) {
                "Unable to persist database encryption key"
            }
            return fresh
        }
    }

    /** Wipes the stored master (Fire Button path). Keeps the DB file itself for the caller to delete. */
    fun wipe(context: Context) {
        synchronized(this) {
            check(prefs(context).edit().clear().commit()) { "Unable to wipe database passphrase record" }
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        // Generate a non-exportable AES-256-GCM key in Android Keystore.
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun wrap(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val out = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(out, 0)
        ciphertext.copyInto(out, iv.size)
        return Base64.getEncoder().encodeToString(out)
    }

    private fun unwrap(stored: String): ByteArray {
        val data = Base64.getDecoder().decode(stored)
        require(data.size > GCM_IV_LEN) { "Stored database key is malformed" }
        val iv = data.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = data.copyOfRange(GCM_IV_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
