package com.example.gavanhomes.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the API token, the signed-in user and the last dashboard on the device, so the app opens instantly.
 * Everything is stored encrypted (AES-256-GCM) with a key that lives in the Android Keystore — it cannot be read out of the
 * phone, so a copy of the app's files (a backup, a rooted device, a lost phone's storage) holds nothing usable.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("gavan_session", Context.MODE_PRIVATE)

    var token: String?
        get() = read("token")
        set(value) = write("token", value)

    /** The signed-in user as JSON (see ApiClient.cachedUser). */
    var userJson: String?
        get() = read("user")
        set(value) = write("user", value)

    /** Last dashboard response — shown straight away while a fresh one loads. */
    var dashboardJson: String?
        get() = read("dashboard")
        set(value) = write("dashboard", value)

    /** Last unread-notification count, so the bell's badge is right from the first frame (just a number — not secret). */
    var unread: Int
        get() = prefs.getInt("unread", 0)
        set(value) { prefs.edit().putInt("unread", value).apply() }

    /** If this phone's Keystore can't encrypt (some budget phones), nothing is stored — the app works, it just asks for a sign-in next time — rather than crashing. */
    private fun write(key: String, value: String?) {
        val sealed = value?.let { runCatching { seal(it) }.getOrNull() }
        prefs.edit().apply { if (sealed == null) remove(key) else putString(key, sealed) }.apply()
    }

    /** null when nothing is stored, or when the stored value can't be opened (an older plain-text install, a changed key): the app then just asks for a sign-in. */
    private fun read(key: String): String? = prefs.getString(key, null)?.let { open(it) }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ── encryption ──
    private companion object {
        const val ALIAS = "gavan_session_key"
        const val PREFIX = "v1:"

        fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }

        /** "v1:" + base64(iv + ciphertext) */
        fun seal(plain: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun open(stored: String): String? = runCatching {
            if (!stored.startsWith(PREFIX)) return null
            val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
}
