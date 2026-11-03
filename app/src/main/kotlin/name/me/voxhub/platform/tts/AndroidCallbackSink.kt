package name.me.voxhub.platform.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CancellationException
import name.me.voxhub.core.tts.AudioSink
import name.me.voxhub.core.tts.PcmEncoding
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.TtsError
import name.me.voxhub.core.tts.TtsFailure

internal enum class CallbackState {
    NEW,
    STARTED,
    TERMINAL,
}

internal class AndroidCallbackSink(
    private val callback: SynthesisCallback,
) : AudioSink {
    private var state = CallbackState.NEW
    private var pendingByte: Byte? = null
    private var chunkLimit = 0
    private val lock = Any()

    override suspend fun start(format: PcmFormat) = synchronized(lock) {
        if (state != CallbackState.NEW) return@synchronized
        if (format.encoding != PcmEncoding.S16_LE || format.channelCount != 1) {
            terminateWithError(TextToSpeech.ERROR_SYNTHESIS)
            throw outputFailure("UNSUPPORTED_PCM_FORMAT")
        }
        chunkLimit = callback.maxBufferSize - (callback.maxBufferSize % PCM_FRAME_SIZE)
        if (chunkLimit < PCM_FRAME_SIZE) {
            terminateWithError(TextToSpeech.ERROR_OUTPUT)
            throw outputFailure("INVALID_CALLBACK_BUFFER")
        }
        if (callback.start(format.sampleRateHz, AudioFormat.ENCODING_PCM_16BIT, format.channelCount) != TextToSpeech.SUCCESS) {
            state = CallbackState.TERMINAL
            throw CallbackTerminatedException()
        }
        state = CallbackState.STARTED
    }

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = synchronized(lock) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) { "Invalid audio range" }
        if (state == CallbackState.TERMINAL) throw CallbackTerminatedException()
        check(state == CallbackState.STARTED) { "Audio sink has not started" }
        if (length == 0) return@synchronized

        var position = offset
        val end = offset + length

        pendingByte?.let {
            send(byteArrayOf(it, bytes[position++]), 0, PCM_FRAME_SIZE)
            pendingByte = null
        }

        val alignedEnd = end - (end - position) % PCM_FRAME_SIZE
        while (position < alignedEnd) {
            val count = minOf(chunkLimit, alignedEnd - position)
            send(bytes, position, count)
            position += count
        }
        if (position < end) pendingByte = bytes[position]
    }

    override suspend fun complete() = synchronized(lock) {
        if (state == CallbackState.TERMINAL) throw CallbackTerminatedException()
        if (state != CallbackState.STARTED || pendingByte != null) {
            terminateWithError(TextToSpeech.ERROR_SYNTHESIS)
            throw outputFailure("INCOMPLETE_PCM_OUTPUT")
        }
        state = CallbackState.TERMINAL
        if (callback.done() != TextToSpeech.SUCCESS) throw CallbackTerminatedException()
    }

    private fun outputFailure(code: String) = name.me.voxhub.core.tts.TtsException(
        TtsFailure.OUTPUT,
        name.me.voxhub.core.tts.TtsDiagnostic(stage = name.me.voxhub.core.tts.TtsStage.OUTPUT, reasonCode = code),
    )

    override suspend fun fail(error: TtsError) = synchronized(lock) {
        if (state == CallbackState.TERMINAL) return@synchronized
        terminateWithError(error.failure.toAndroidError())
    }

    private fun send(bytes: ByteArray, offset: Int, length: Int) {
        if (callback.audioAvailable(bytes, offset, length) != TextToSpeech.SUCCESS) {
            state = CallbackState.TERMINAL
            throw CallbackTerminatedException()
        }
    }

    private fun terminateWithError(errorCode: Int) {
        if (state == CallbackState.TERMINAL) return
        state = CallbackState.TERMINAL
        callback.error(errorCode)
    }

    private companion object {
        const val PCM_FRAME_SIZE = 2
    }
}

internal class CallbackTerminatedException : CancellationException("Android callback stopped accepting audio")

internal fun TtsFailure.toAndroidError(): Int = when (this) {
    TtsFailure.INVALID_REQUEST -> TextToSpeech.ERROR_INVALID_REQUEST
    TtsFailure.NETWORK -> TextToSpeech.ERROR_NETWORK
    TtsFailure.NETWORK_TIMEOUT -> TextToSpeech.ERROR_NETWORK_TIMEOUT
    TtsFailure.SERVICE -> TextToSpeech.ERROR_SERVICE
    TtsFailure.SYNTHESIS -> TextToSpeech.ERROR_SYNTHESIS
    TtsFailure.OUTPUT -> TextToSpeech.ERROR_OUTPUT
    TtsFailure.CANCELLED -> TextToSpeech.STOPPED
}
