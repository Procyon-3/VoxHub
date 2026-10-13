package name.me.voxhub.platform.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ProviderCredentialCipher {
    fun encrypt(apiKey: String): String
    fun decryptOrNull(encoded: String): String?
}

class AndroidProviderCredentialCipher : ProviderCredentialCipher {
    override fun encrypt(apiKey: String): String {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "API key must not be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(2 + cipher.iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decryptOrNull(encoded: String): String? = try {
        val buffer = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
        require(buffer.get() == FORMAT_VERSION) { "Unsupported credential format" }
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in 1 until buffer.remaining()) { "Invalid credential IV" }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8).trim().takeIf(String::isNotEmpty)
    } catch (_: Exception) {
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "tts_mimo_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
    }
}
