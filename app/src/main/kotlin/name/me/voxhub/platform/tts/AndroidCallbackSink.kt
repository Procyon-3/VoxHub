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
    private var frameSize = 0
    private var pending = ByteArray(0)
    private val lock = Any()

    override suspend fun start(format: PcmFormat) = synchronized(lock) {
        if (state != CallbackState.NEW) return@synchronized
        if (format.encoding != PcmEncoding.S16_LE || format.channelCount != 1) {
            terminateWithError(TextToSpeech.ERROR_SYNTHESIS)
            throw outputFailure("UNSUPPORTED_PCM_FORMAT")
        }
        frameSize = format.bytesPerFrame
        val maxAligned = alignedMaximum()
        if (maxAligned < frameSize) {
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
        var remaining = length
        val chunkLimit = alignedMaximum()

        if (pending.isNotEmpty()) {
            val needed = frameSize - pending.size
            if (remaining < needed) {
                pending += bytes.copyOfRange(position, position + remaining)
                return@synchronized
            }
            val frame = ByteArray(frameSize)
            pending.copyInto(frame)
            bytes.copyInto(frame, pending.size, position, position + needed)
            send(frame, 0, frame.size)
            pending = ByteArray(0)
            position += needed
            remaining -= needed
        }

        val completeLength = remaining - (remaining % frameSize)
        var sent = 0
        while (sent < completeLength) {
            val count = minOf(chunkLimit, completeLength - sent)
            send(bytes, position + sent, count)
            sent += count
        }
        position += completeLength
        remaining -= completeLength
        if (remaining > 0) {
            pending = bytes.copyOfRange(position, position + remaining)
        }
    }

    override suspend fun complete() = synchronized(lock) {
        if (state == CallbackState.TERMINAL) throw CallbackTerminatedException()
        if (state != CallbackState.STARTED || pending.isNotEmpty()) {
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

    private fun alignedMaximum(): Int = callback.maxBufferSize - (callback.maxBufferSize % frameSize)

    private fun send(bytes: ByteArray, offset: Int, length: Int) {
        if (callback.audioAvailable(bytes, offset, length) != TextToSpeech.SUCCESS) {
            state = CallbackState.TERMINAL
            pending = ByteArray(0)
            throw CallbackTerminatedException()
        }
    }

    private fun terminateWithError(errorCode: Int) {
        if (state == CallbackState.TERMINAL) return
        state = CallbackState.TERMINAL
        pending = ByteArray(0)
        callback.error(errorCode)
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
