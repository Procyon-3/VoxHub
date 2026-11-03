package name.me.voxhub.provider.mimo.protocol

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.PcmStream
import name.me.voxhub.core.tts.TtsException
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsStage
import okhttp3.Call
import okhttp3.Response
import okio.BufferedSource
import okio.ByteString.Companion.decodeBase64

/** Existing MiMo response contracts; model-specific requests remain in each Provider. */
enum class MimoStreamProtocol { PRESET, GENERATED }

class MimoSsePcmStream(
    private val call: Call,
    private val response: Response,
    private val source: BufferedSource,
    override val format: PcmFormat,
    private val executor: ExecutorService,
    private val protocol: MimoStreamProtocol,
) : PcmStream {
    private val closed = AtomicBoolean(false)
    private var pending = ByteArray(0)
    private var pendingOffset = 0
    private var totalAudioBytes = 0L
    private var receivedAudio = false
    private var finished = false

    override suspend fun read(target: ByteArray): Int {
        require(target.isNotEmpty()) { "Target buffer must not be empty" }
        coroutineContext.ensureActive()
        if (pendingOffset < pending.size) return copyPending(target)
        if (finished) return -1
        return readNextChunk(target)
    }

    private suspend fun readNextChunk(target: ByteArray): Int =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { close() }
            try {
                executor.execute {
                    try {
                        val result = readNextChunkBlocking(target)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (cancelled: CancellationException) {
                        if (continuation.isActive) continuation.cancel(cancelled)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error.toTtsException(protocol))
                    }
                }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error.toTtsException(protocol))
            }
        }

    private fun readNextChunkBlocking(target: ByteArray): Int {
        while (!closed.get()) {
            val data = when (protocol) {
                MimoStreamProtocol.PRESET -> readDataLine()
                MimoStreamProtocol.GENERATED -> readEvent()
            }
            if (data == "[DONE]") {
                if (!receivedAudio || totalAudioBytes % format.bytesPerFrame != 0L) {
                    throw protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_PARSE, "INVALID_PCM_ALIGNMENT")
                }
                finished = true
                return -1
            }
            if (data.isEmpty()) continue
            val root = try {
                Json.parseToJsonElement(data).jsonObject
            } catch (error: SerializationException) {
                throw protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_PARSE, "INVALID_SSE_JSON", cause = error.takeIf { protocol == MimoStreamProtocol.PRESET })
            } catch (error: IllegalArgumentException) {
                throw protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_PARSE, "INVALID_SSE_JSON", cause = error.takeIf { protocol == MimoStreamProtocol.PRESET })
            }
            if (root["error"] != null) {
                throw protocolError(TtsFailure.SERVICE, TtsStage.STREAM_PARSE, "UPSTREAM_STREAM_ERROR")
            }
            val encodedAudio = root.audioDataOrNull() ?: continue
            val decoded = encodedAudio.takeIf {
                protocol == MimoStreamProtocol.PRESET || (it.length % 4 == 0 && BASE64.matches(it))
            }?.decodeBase64()?.toByteArray()
                ?: throw protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_PARSE, "INVALID_BASE64")
            if (decoded.isEmpty()) continue
            receivedAudio = true
            totalAudioBytes += decoded.size
            pending = decoded
            pendingOffset = 0
            return copyPending(target)
        }
        throw CancellationException("MiMo stream was closed")
    }

    private fun readDataLine(): String {
        while (!closed.get()) {
            val line = source.readUtf8Line()
                ?: throw protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_READ, "MISSING_DONE")
            if (line.startsWith("data:")) return line.substringAfter("data:").trimStart()
        }
        throw CancellationException("MiMo stream was closed")
    }

    private fun readEvent(): String {
        val data = StringBuilder()
        var hasData = false
        while (!closed.get()) {
            val line = source.readUtf8Line()
                ?: throw protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_READ, "MISSING_DONE")
            if (line.isEmpty()) {
                if (hasData) return data.toString()
                continue
            }
            if (line.startsWith(":")) continue
            val field = line.substringBefore(':')
            val value = line.substringAfter(':', "").removePrefix(" ")
            if (field == "data") {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        throw CancellationException("MiMo stream was closed")
    }

    private fun copyPending(target: ByteArray): Int {
        val count = minOf(target.size, pending.size - pendingOffset)
        pending.copyInto(target, endIndex = pendingOffset + count, startIndex = pendingOffset)
        pendingOffset += count
        if (pendingOffset == pending.size) {
            pending = ByteArray(0)
            pendingOffset = 0
        }
        return count
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        call.cancel()
        response.close()
        pending = ByteArray(0)
        pendingOffset = 0
    }
}

private fun JsonObject.audioDataOrNull(): String? = try {
    STREAM_JSON.decodeFromJsonElement<AudioEvent>(this).choices.firstOrNull()?.delta?.audio?.data
} catch (_: SerializationException) {
    throw malformedStream()
}

@Serializable
private data class AudioEvent(val choices: List<AudioChoice> = emptyList())

@Serializable
private data class AudioChoice(val delta: AudioDelta? = null)

@Serializable
private data class AudioDelta(val audio: AudioData? = null)

@Serializable
private data class AudioData(val data: String? = null)

private val STREAM_JSON = Json { ignoreUnknownKeys = true }

private fun Exception.toTtsException(protocol: MimoStreamProtocol): Exception = when (this) {
    is TtsException -> this
    is InterruptedIOException -> protocolError(TtsFailure.NETWORK_TIMEOUT, TtsStage.STREAM_READ, "STREAM_TIMEOUT", cause = takeIf { protocol == MimoStreamProtocol.PRESET })
    is IOException -> protocolError(TtsFailure.NETWORK, TtsStage.STREAM_READ, "STREAM_IO_FAILURE", cause = takeIf { protocol == MimoStreamProtocol.PRESET })
    else -> protocolError(TtsFailure.SYNTHESIS, TtsStage.STREAM_PARSE, "UNEXPECTED_STREAM_ERROR", cause = takeIf { protocol == MimoStreamProtocol.PRESET })
}

private fun malformedStream() = protocolError(
    TtsFailure.SYNTHESIS,
    TtsStage.STREAM_PARSE,
    "INVALID_SSE_STRUCTURE",
)

private val BASE64 = Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")
