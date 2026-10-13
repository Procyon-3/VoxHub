package name.me.voxhub.provider.mimo.voicedesign

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
import name.me.voxhub.provider.mimo.protocol.protocolError
import name.me.voxhub.provider.mimo.protocol.requireSseBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

class MimoVoiceDesignProviderConfig(val apiKey: String, val voiceDesignPrompt: String, val endpoint: String = MIMO_API_ENDPOINT)

class MimoVoiceDesignProviderFactory internal constructor(
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

    fun create(config: MimoVoiceDesignProviderConfig): TtsProvider = VoiceDesignTtsProvider(
        apiKey = config.apiKey,
        voiceDesignPrompt = config.voiceDesignPrompt,
        client = client,
        endpoint = config.endpoint.toHttpUrl(),
    )
}

internal class VoiceDesignTtsProvider(
    private val apiKey: String,
    private val voiceDesignPrompt: String,
    internal val client: OkHttpClient,
    private val endpoint: HttpUrl,
) : TtsProvider {
    override val descriptor: TtsProviderDescriptor = VOICEDESIGN_DESCRIPTOR

    override val voices: List<TtsVoice> = VOICEDESIGN_VOICES

    override suspend fun openPcmStream(request: TtsRequest): PcmStream {
        validateRequest(request)
        if (apiKey.isBlank()) {
            throw protocolError(TtsFailure.SERVICE, TtsStage.REQUEST_VALIDATION, "MISSING_CREDENTIAL")
        }
        if (apiKey.any { it.code !in 32..126 }) {
            throw protocolError(TtsFailure.SERVICE, TtsStage.REQUEST_VALIDATION, "INVALID_CREDENTIAL")
        }
        if (voiceDesignPrompt.isBlank()) throw protocolError(TtsFailure.INVALID_REQUEST, TtsStage.REQUEST_VALIDATION, "MISSING_VOICE_DESIGN_PROMPT")
        val requestBody = buildRequestBody(request.text, voiceDesignPrompt)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val call = client.newCall(
            Request.Builder()
                .url(endpoint)
                .header(API_KEY_HEADER, apiKey)
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
            format = VOICEDESIGN_OUTPUT_FORMAT,
            executor = client.dispatcher.executorService,
            protocol = MimoStreamProtocol.GENERATED,
        )
    }

    private fun validateRequest(request: TtsRequest) {
        if (request.text.isEmpty()) {
            throw protocolError(TtsFailure.INVALID_REQUEST, TtsStage.REQUEST_VALIDATION, "EMPTY_TEXT")
        }
        if (request.locale !in VOICEDESIGN_SUPPORTED_LOCALES) {
            throw protocolError(TtsFailure.INVALID_REQUEST, TtsStage.REQUEST_VALIDATION, "UNSUPPORTED_LOCALE")
        }
        if (VOICEDESIGN_VOICES.none { it.id == request.voiceId }) {
            throw protocolError(TtsFailure.INVALID_REQUEST, TtsStage.REQUEST_VALIDATION, "UNKNOWN_VOICE")
        }
    }

    private companion object {
        const val API_KEY_HEADER = "api-key"
        const val EVENT_STREAM_MEDIA_TYPE = "text/event-stream"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        fun buildRequestBody(text: String, prompt: String) = buildJsonObject {
            put("model", "mimo-v2.5-tts-voicedesign")
            put("messages", buildJsonArray {
                add(buildJsonObject {
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
                put("optimize_text_preview", false)
            })
            put("stream", true)
        }
    }
}

val VOICEDESIGN_ZH_CN_LOCALE = LocaleTag("zh-CN")
val VOICEDESIGN_EN_US_LOCALE = LocaleTag("en-US")
private val VOICEDESIGN_SUPPORTED_LOCALES = setOf(VOICEDESIGN_ZH_CN_LOCALE, VOICEDESIGN_EN_US_LOCALE)
val VOICEDESIGN_VOICES = listOf(
    TtsVoice("mimo-voicedesign/zh-CN-designed", "设计音色", VOICEDESIGN_ZH_CN_LOCALE),
    TtsVoice("mimo-voicedesign/en-US-designed", "设计音色", VOICEDESIGN_EN_US_LOCALE),
)

const val VOICEDESIGN_DEFAULT_VOICE_ID = "mimo-voicedesign/zh-CN-designed"

val VOICEDESIGN_OUTPUT_FORMAT = PcmFormat(
    sampleRateHz = 24_000,
    channelCount = 1,
    encoding = PcmEncoding.S16_LE,
)

val VOICEDESIGN_DESCRIPTOR = TtsProviderDescriptor(
    id = "mimo-voicedesign",
    displayName = "MiMo-V2.5-TTS-VoiceDesign",
    outputFormat = VOICEDESIGN_OUTPUT_FORMAT,
)
