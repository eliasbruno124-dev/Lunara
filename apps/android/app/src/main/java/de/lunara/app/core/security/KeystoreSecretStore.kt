package de.lunara.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SecretStore {
    fun putString(key: String, value: String?)
    fun getString(key: String): String?
    fun putBytes(key: String, value: ByteArray?)
    fun getBytes(key: String): ByteArray?
    fun clear()
}

@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val prefs = context.getSharedPreferences("lunara_encrypted_secrets", Context.MODE_PRIVATE)
    private val alias = "lunara.local-secrets.v1"

    override fun putString(key: String, value: String?) = putBytes(key, value?.encodeToByteArray())

    override fun getString(key: String): String? = getBytes(key)?.decodeToString()

    @Synchronized
    override fun putBytes(key: String, value: ByteArray?) {
        if (value == null) {
            check(prefs.edit().remove(key).commit()) {
                "Encrypted secret could not be removed"
            }
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(key.encodeToByteArray())
        val encrypted = cipher.doFinal(value)
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        try {
            check(prefs.edit().putString(key, payload).commit()) {
                "Encrypted secret could not be persisted"
            }
        } finally {
            value.fill(0)
        }
    }

    @Synchronized
    override fun getBytes(key: String): ByteArray? {
        val payload = prefs.getString(key, null) ?: return null
        return runCatching {
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            require(decoded.size > IV_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, decoded.copyOfRange(0, IV_SIZE)),
            )
            cipher.updateAAD(key.encodeToByteArray())
            cipher.doFinal(decoded.copyOfRange(IV_SIZE, decoded.size))
        }.getOrNull()
    }

    @Synchronized
    override fun clear() {
        check(prefs.edit().clear().commit()) { "Encrypted secret storage could not be cleared" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
        check(!store.containsAlias(alias)) { "Android Keystore alias could not be deleted" }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object { const val IV_SIZE = 12 }
}
