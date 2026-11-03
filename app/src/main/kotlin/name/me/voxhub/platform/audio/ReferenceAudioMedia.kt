package name.me.voxhub.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import name.me.voxhub.provider.mimo.voiceclone.ReferenceAudio

class ImportedReferenceAudio(val audio: ReferenceAudio, val displayName: String)

internal class MemoryAudioSource(private var bytes: ByteArray) : MediaDataSource() {
    @Synchronized override fun getSize(): Long = bytes.size.toLong()
    @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        require(position >= 0 && offset >= 0 && size >= 0 && offset <= buffer.size - size)
        if (size == 0) return 0
        if (position >= bytes.size) return -1
        val count = minOf(size, bytes.size - position.toInt())
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
        return count
    }
    @Synchronized override fun close() { bytes = ByteArray(0) }
}

class ReferenceAudioImporter(private val context: Context) {
    suspend fun import(uri: Uri): ImportedReferenceAudio = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        val resolver = context.contentResolver
        val bytes = requireNotNull(resolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.isNotEmpty())
        val wav = bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
        val source = MemoryAudioSource(bytes)
        val extractor = MediaExtractor()
        val mime = try {
            extractor.setDataSource(source)
            require(extractor.trackCount == 1)
            val track = requireNotNull(extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME))
            require(track.startsWith("audio/"))
            require(wav || track == "audio/mpeg")
            extractor.selectTrack(0)
            require(extractor.sampleTime >= 0)
            if (wav) "audio/wav" else "audio/mpeg"
        } finally {
            extractor.release()
            source.close()
        }
        val audio = ReferenceAudio(bytes, mime)
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.take(256).orEmpty()
        ImportedReferenceAudio(audio, displayName)
    }
}

/** Foreground-only preview; never writes decrypted audio to disk. Calls run on main. */
class ReferenceAudioPlayer(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var source: MemoryAudioSource? = null
    private var focus: AudioFocusRequest? = null
    private var finished: ((Boolean) -> Unit)? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { if (it < 0) stop() }

    fun play(audio: ReferenceAudio, onFinished: (Boolean) -> Unit) {
        stop()
        finished = onFinished
        try {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            check(requestFocus(attributes) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val input = MemoryAudioSource(audio.copyBytes()).also { source = it }
            val current = MediaPlayer().also { player = it }
            current.setAudioAttributes(attributes)
            current.setDataSource(input)
            current.setOnPreparedListener { if (player === it) it.start() }
            current.setOnCompletionListener { if (player === it) stop() }
            current.setOnErrorListener { value, _, _ ->
                if (player === value) stop(failed = true)
                true
            }
            current.prepareAsync()
        } catch (_: Exception) { stop(failed = true) }
    }

    fun stop(failed: Boolean = false) {
        val current = player
        player = null
        current?.release()
        source?.close()
        source = null
        abandonFocus()
        val callback = finished
        finished = null
        callback?.invoke(failed)
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(attributes: AudioAttributes): Int =
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener).build()
            focus = request
            manager.requestAudioFocus(request)
        } else manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focus?.let(manager::abandonAudioFocusRequest)
        else manager.abandonAudioFocus(listener)
        focus = null
    }
}
