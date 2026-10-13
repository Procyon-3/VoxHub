package name.me.voxhub.core.tts


data class LocaleTag(val value: String) {
    init {
        require(value.isNotBlank()) { "Locale tag must not be blank" }
    }
}

data class TtsVoice(
    val id: String,
    val displayName: String,
    val locale: LocaleTag,
) {
    init {
        require(id.isNotBlank()) { "Voice id must not be blank" }
        require(displayName.isNotBlank()) { "Voice display name must not be blank" }
    }
}

data class TtsRequest(
    val text: String,
    val locale: LocaleTag,
    val voiceId: String,
)

data class TtsProviderDescriptor(
    val id: String,
    val displayName: String,
    val outputFormat: PcmFormat,
) {
    init {
        require(PROVIDER_ID.matches(id)) { "Invalid Provider id" }
        require(displayName.isNotBlank()) { "Provider display name must not be blank" }
    }

    private companion object {
        val PROVIDER_ID = Regex("[a-z0-9][a-z0-9-]*")
    }
}

enum class PcmEncoding(val bytesPerSample: Int) {
    S16_LE(bytesPerSample = 2),
}

data class PcmFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: PcmEncoding,
) {
    init {
        require(sampleRateHz > 0) { "Sample rate must be positive" }
        require(channelCount > 0) { "Channel count must be positive" }
    }

    val bytesPerFrame: Int = channelCount * encoding.bytesPerSample
}

enum class TtsFailure {
    INVALID_REQUEST,
    NETWORK,
    NETWORK_TIMEOUT,
    SERVICE,
    SYNTHESIS,
    OUTPUT,
    CANCELLED,
}

enum class TtsStage {
    REQUEST_VALIDATION,
    NETWORK_CONNECT,
    HTTP_RESPONSE,
    STREAM_READ,
    STREAM_PARSE,
    OUTPUT,
}

data class TtsDiagnostic(
    val providerStatus: Int? = null,
    val providerCode: String? = null,
    val stage: TtsStage? = null,
    val reasonCode: String? = null,
) {
    init {
        require(providerStatus == null || providerStatus in 100..599) { "Invalid Provider status" }
        require(providerCode == null || SAFE_CODE.matches(providerCode)) { "Invalid Provider code" }
        require(reasonCode == null || SAFE_CODE.matches(reasonCode)) { "Invalid reason code" }
    }

    private companion object {
        val SAFE_CODE = Regex("[A-Z0-9_]{1,64}")
    }
}

data class TtsError(
    val failure: TtsFailure,
    val diagnostic: TtsDiagnostic? = null,
)

class TtsException(
    val error: TtsError,
    cause: Throwable? = null,
) : Exception(null, cause) {
    constructor(
        failure: TtsFailure,
        diagnostic: TtsDiagnostic? = null,
        cause: Throwable? = null,
    ) : this(TtsError(failure, diagnostic), cause)
}
