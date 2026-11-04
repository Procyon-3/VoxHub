package name.me.voxhub.provider.mimo.voiceclone

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import name.me.voxhub.core.tts.LocaleTag
import name.me.voxhub.core.tts.PcmEncoding
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.PcmStream
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsProvider
import name.me.voxhub.core.tts.TtsProviderDescriptor
import name.me.voxhub.core.tts.TtsRequest
import name.me.voxhub.core.tts.TtsStage
import name.me.voxhub.core.tts.TtsVoice
import name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT
import name.me.voxhub.provider.mimo.protocol.MimoSsePcmStream
import name.me.voxhub.provider.mimo.protocol.MimoStreamProtocol
import name.me.voxhub.provider.mimo.protocol.mimoApiKey
import name.me.voxhub.provider.mimo.protocol.protocolError
import name.me.voxhub.provider.mimo.protocol.requireSseBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import okio.ByteString.Companion.toByteString

const val MAX_REFERENCE_AUDIO_BYTES = 10_000_000

class ReferenceAudio(bytes: ByteArray, val mimeType: String) {
    private val content = bytes.copyOf()
    val size: Int get() = content.size
    init {
        require(mimeType == "audio/wav" || mimeType == "audio/mpeg")
        require(content.isNotEmpty())
    }
    fun copyBytes(): ByteArray = content.copyOf()
    internal fun dataUrl(): String = "data:$mimeType;base64," + content.toByteString().base64()
}
class MimoVoiceCloneProviderConfig(val apiKey: String, val referenceAudio: ReferenceAudio, val styleInstruction: String = "", val endpoint: String = MIMO_API_ENDPOINT)

class MimoVoiceCloneProviderFactory(
    private val client: OkHttpClient,
) {
    fun create(config: MimoVoiceCloneProviderConfig): TtsProvider = VoiceCloneTtsProvider(
        apiKey = config.apiKey,
        referenceAudio = config.referenceAudio,
        styleInstruction = config.styleInstruction,
        client = client,
        endpoint = config.endpoint.toHttpUrl(),
    )
}

internal class VoiceCloneTtsProvider(
    private val apiKey: String,
    private val referenceAudio: ReferenceAudio,
    private val styleInstruction: String,
    internal val client: OkHttpClient,
    private val endpoint: HttpUrl,
) : TtsProvider {
    override val descriptor: TtsProviderDescriptor = VOICECLONE_DESCRIPTOR

    override val voices: List<TtsVoice> = VOICECLONE_VOICES

    override suspend fun openPcmStream(request: TtsRequest): PcmStream {
        if (referenceAudio.size > MAX_REFERENCE_AUDIO_BYTES) {
            throw protocolError(TtsFailure.INVALID_REQUEST, TtsStage.REQUEST_VALIDATION, "REFERENCE_AUDIO_TOO_LARGE")
        }
        val requestBody = withContext(Dispatchers.Default) {
            buildRequestBody(request.text, styleInstruction, referenceAudio)
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
        }
        val call = client.newCall(
            Request.Builder()
                .url(endpoint)
                .mimoApiKey(apiKey)
                .header("Accept", EVENT_STREAM_MEDIA_TYPE)
                .post(requestBody)
                .build(),
        )

        val response = try {
            call.executeAsync()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: InterruptedIOException) {
            throw protocolError(TtsFailure.NETWORK_TIMEOUT, TtsStage.NETWORK_CONNECT, "REQUEST_TIMEOUT")
        } catch (network: IOException) {
            throw protocolError(TtsFailure.NETWORK, TtsStage.NETWORK_CONNECT, "NETWORK_FAILURE")
        }

        val body = response.requireSseBody()
        return MimoSsePcmStream(
            call = call,
            response = response,
            source = body.source(),
            format = VOICECLONE_OUTPUT_FORMAT,
            executor = client.dispatcher.executorService,
            protocol = MimoStreamProtocol.GENERATED,
        )
    }

    private companion object {
        const val EVENT_STREAM_MEDIA_TYPE = "text/event-stream"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        fun buildRequestBody(text: String, prompt: String, audio: ReferenceAudio) = buildJsonObject {
            put("model", "mimo-v2.5-tts-voiceclone")
            put("messages", buildJsonArray {
                if (prompt.isNotBlank()) add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", text)
                })
            })
            put("audio", buildJsonObject {
                put("format", "pcm16")
                put("voice", audio.dataUrl())
            })
            put("stream", true)
        }
    }
}

val VOICECLONE_ZH_CN_LOCALE = LocaleTag("zh-CN")
val VOICECLONE_EN_US_LOCALE = LocaleTag("en-US")
val VOICECLONE_VOICES = listOf(
    TtsVoice("mimo-voiceclone/zh-CN-cloned", "克隆音色", VOICECLONE_ZH_CN_LOCALE),
    TtsVoice("mimo-voiceclone/en-US-cloned", "克隆音色", VOICECLONE_EN_US_LOCALE),
)

const val VOICECLONE_DEFAULT_VOICE_ID = "mimo-voiceclone/zh-CN-cloned"

val VOICECLONE_OUTPUT_FORMAT = PcmFormat(
    sampleRateHz = 24_000,
    channelCount = 1,
    encoding = PcmEncoding.S16_LE,
)

val VOICECLONE_DESCRIPTOR = TtsProviderDescriptor(
    id = "mimo-voiceclone",
    displayName = "MiMo-V2.5-TTS-VoiceClone",
    outputFormat = VOICECLONE_OUTPUT_FORMAT,
)
