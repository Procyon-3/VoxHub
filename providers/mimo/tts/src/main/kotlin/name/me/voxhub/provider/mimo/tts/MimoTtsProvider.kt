package name.me.voxhub.provider.mimo.tts

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
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
import name.me.voxhub.provider.mimo.protocol.createMimoHttpClient
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

data class MimoProviderConfig(val apiKey: String, val endpoint: String = MIMO_API_ENDPOINT)

class MimoProviderFactory internal constructor(
    requestTimeoutMillis: Long,
    internal val client: OkHttpClient,
) {
    constructor(requestTimeoutMillis: Long) : this(
        requestTimeoutMillis,
        createMimoHttpClient(requestTimeoutMillis),
    )

    init {
        require(requestTimeoutMillis > 0) { "Request timeout must be positive" }
    }

    fun create(config: MimoProviderConfig): TtsProvider = MimoTtsProvider(
        apiKey = config.apiKey,
        client = client,
        endpoint = config.endpoint.toHttpUrl(),
    )
}

internal class MimoTtsProvider(
    private val apiKey: String,
    internal val client: OkHttpClient,
    private val endpoint: HttpUrl,
) : TtsProvider {
    override val descriptor: TtsProviderDescriptor = MIMO_DESCRIPTOR

    override val voices: List<TtsVoice> = MIMO_VOICES

    override suspend fun openPcmStream(request: TtsRequest): PcmStream {
        val upstreamVoice = VOICE_MAPPINGS.first { it.localId == request.voiceId }.upstreamName
        val requestBody = buildRequestBody(request.text, upstreamVoice)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
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
            throw protocolError(TtsFailure.NETWORK_TIMEOUT, TtsStage.NETWORK_CONNECT, "REQUEST_TIMEOUT", cause = timeout)
        } catch (network: IOException) {
            throw protocolError(TtsFailure.NETWORK, TtsStage.NETWORK_CONNECT, "NETWORK_FAILURE", cause = network)
        }

        val body = response.requireSseBody()
        return MimoSsePcmStream(
            call = call,
            response = response,
            source = body.source(),
            format = MIMO_OUTPUT_FORMAT,
            executor = client.dispatcher.executorService,
            protocol = MimoStreamProtocol.PRESET,
        )
    }

    private companion object {
        const val EVENT_STREAM_MEDIA_TYPE = "text/event-stream"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        fun buildRequestBody(text: String, upstreamVoice: String) = buildJsonObject {
            put("model", "mimo-v2.5-tts")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", text)
                })
            })
            put("audio", buildJsonObject {
                put("format", "pcm16")
                put("voice", upstreamVoice)
            })
            put("stream", true)
        }
    }
}

private data class VoiceMapping(
    val localId: String,
    val displayName: String,
    val upstreamName: String,
    val locale: LocaleTag,
)

val MIMO_ZH_CN_LOCALE = LocaleTag("zh-CN")
val MIMO_EN_US_LOCALE = LocaleTag("en-US")

private val VOICE_MAPPINGS = listOf(
    VoiceMapping("mimo/zh-CN-bingtang", "冰糖", "冰糖", MIMO_ZH_CN_LOCALE),
    VoiceMapping("mimo/zh-CN-moli", "茉莉", "茉莉", MIMO_ZH_CN_LOCALE),
    VoiceMapping("mimo/zh-CN-suda", "苏打", "苏打", MIMO_ZH_CN_LOCALE),
    VoiceMapping("mimo/zh-CN-baihua", "白桦", "白桦", MIMO_ZH_CN_LOCALE),
    VoiceMapping("mimo/en-US-mia", "Mia", "Mia", MIMO_EN_US_LOCALE),
    VoiceMapping("mimo/en-US-chloe", "Chloe", "Chloe", MIMO_EN_US_LOCALE),
    VoiceMapping("mimo/en-US-milo", "Milo", "Milo", MIMO_EN_US_LOCALE),
    VoiceMapping("mimo/en-US-dean", "Dean", "Dean", MIMO_EN_US_LOCALE),
)

val MIMO_VOICES: List<TtsVoice> = VOICE_MAPPINGS.map { mapping ->
    TtsVoice(
        id = mapping.localId,
        displayName = mapping.displayName,
        locale = mapping.locale,
    )
}

const val MIMO_DEFAULT_VOICE_ID = "mimo/zh-CN-bingtang"

val MIMO_OUTPUT_FORMAT = PcmFormat(
    sampleRateHz = 24_000,
    channelCount = 1,
    encoding = PcmEncoding.S16_LE,
)

val MIMO_DESCRIPTOR = TtsProviderDescriptor(
    id = "mimo",
    displayName = "MiMo-V2.5-TTS",
    outputFormat = MIMO_OUTPUT_FORMAT,
)
