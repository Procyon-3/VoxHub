package name.me.voxhub.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import name.me.voxhub.core.tts.AudioSink
import name.me.voxhub.core.tts.PcmEncoding
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.TtsDiagnostic
import name.me.voxhub.core.tts.TtsError
import name.me.voxhub.core.tts.TtsException
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsStage

class PlaybackAudioSink(
    context: Context,
    private val onStarted: () -> Unit,
) : AudioSink {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val lock = Any()
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var sampleRateHz = 0
    private var writtenFrames = 0L

    @Volatile
    var error: TtsError? = null
        private set

    override suspend fun start(format: PcmFormat) {
        require(format.encoding == PcmEncoding.S16_LE && format.channelCount == 1)
        val minimum = AudioTrack.getMinBufferSize(
            format.sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimum <= 0) throw outputError("AUDIO_TRACK_BUFFER_UNAVAILABLE")
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minimum, 16 * 1024))
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw outputError("AUDIO_TRACK_INITIALIZATION_FAILED")
        }
        requestAudioFocus(attributes)
        synchronized(lock) {
            audioTrack = track
            sampleRateHz = format.sampleRateHz
            writtenFrames = 0L
        }
        track.play()
        onStarted()
    }

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        val track = synchronized(lock) { audioTrack }
            ?: throw outputError("AUDIO_TRACK_NOT_ACTIVE")
        val written = withContext(Dispatchers.IO) {
            track.write(bytes, offset, length, AudioTrack.WRITE_BLOCKING)
        }
        if (written != length) throw outputError("AUDIO_TRACK_REJECTED_PCM")
        synchronized(lock) { writtenFrames += written / 2L }
    }

    override suspend fun complete() {
        try {
            val snapshot = synchronized(lock) {
                Triple(audioTrack, writtenFrames, sampleRateHz)
            }
            val track = snapshot.first ?: return
            val playedFrames = runCatching { track.playbackHeadPosition.toLong() }.getOrDefault(snapshot.second)
            val remainingFrames = (snapshot.second - playedFrames).coerceAtLeast(0L)
            if (snapshot.third > 0 && remainingFrames > 0) {
                delay((remainingFrames * 1_000L / snapshot.third + 40L).coerceAtMost(2_000L))
            }
        } finally {
            release()
        }
    }

    override suspend fun fail(error: TtsError) {
        this.error = error
        release()
    }

    fun stopNow() {
        release()
    }

    private fun release() {
        val track = synchronized(lock) {
            audioTrack.also { audioTrack = null }
        }
        if (track != null) {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        abandonAudioFocus()
    }

    private fun outputError(reasonCode: String) = TtsException(
        TtsFailure.OUTPUT,
        TtsDiagnostic(stage = TtsStage.OUTPUT, reasonCode = reasonCode),
    )

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(attributes: AudioAttributes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }
}
