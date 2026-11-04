package name.me.voxhub.data.audio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import name.me.voxhub.provider.mimo.voiceclone.ReferenceAudio

/** Immutable encrypted versions. Repository mutex owns publication and deletion. */
class VoiceCloneAudioStore(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, "voiceclone-audio"))

    private fun file(version: String): File {
        require(UUID.fromString(version).toString() == version)
        return File(directory, version)
    }

    fun write(audio: ReferenceAudio): String {
        check(directory.isDirectory || directory.mkdirs())
        val version = UUID.randomUUID().toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(version.toByteArray(Charsets.US_ASCII))
        val encrypted = cipher.doFinal(audio.copyBytes())
        val target = AtomicFile(file(version))
        val stream = target.startWrite()
        try {
            stream.write(cipher.iv)
            stream.write(encrypted)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
        return version
    }

    fun read(version: String, mime: String): ReferenceAudio {
        val target = file(version)
        require(target.length() >= 29L)
        val payload = target.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        cipher.updateAAD(version.toByteArray(Charsets.US_ASCII))
        return ReferenceAudio(cipher.doFinal(payload, 12, payload.size - 12), mime)
    }

    fun delete(version: String) { AtomicFile(file(version)).delete() }

    fun cleanExcept(versions: Set<String>) {
        directory.listFiles()?.filter { it.isFile && it.name !in versions }?.forEach { it.delete() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private companion object { const val KEY_ALIAS = "voxhub_voiceclone_audio_v1" }
}
