package name.me.voxhub.provider.mimo.protocol

import java.util.concurrent.TimeUnit
import name.me.voxhub.core.tts.TtsDiagnostic
import name.me.voxhub.core.tts.TtsError
import name.me.voxhub.core.tts.TtsException
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsStage
import okhttp3.OkHttpClient
import okhttp3.Response

fun createMimoHttpClient(requestTimeoutMillis: Long): OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(false)
    .connectTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
    .readTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
    .writeTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
    .callTimeout(requestTimeoutMillis, TimeUnit.MILLISECONDS)
    .build()

private fun Int.toFailure(): TtsFailure = when (this) {
    400 -> TtsFailure.INVALID_REQUEST
    408 -> TtsFailure.NETWORK_TIMEOUT
    401, 403, 429 -> TtsFailure.SERVICE
    in 500..599 -> TtsFailure.SERVICE
    else -> TtsFailure.SERVICE
}

private fun Int.toReasonCode(): String = when (this) {
    401, 403 -> "AUTHENTICATION_FAILED"
    408 -> "HTTP_TIMEOUT"
    429 -> "RATE_LIMITED"
    in 500..599 -> "UPSTREAM_SERVICE_ERROR"
    else -> "HTTP_ERROR"
}

fun protocolError(
    failure: TtsFailure,
    stage: TtsStage,
    reasonCode: String,
    providerStatus: Int? = null,
    cause: Throwable? = null,
) = TtsException(
    TtsError(
        failure,
        TtsDiagnostic(
            providerStatus = providerStatus,
            stage = stage,
            reasonCode = reasonCode,
        ),
    ),
    cause,
)

fun Response.requireSseBody(): okhttp3.ResponseBody {
    if (!isSuccessful) {
        val failure = code.toFailure()
        val status = code
        close()
        throw protocolError(
            failure,
            TtsStage.HTTP_RESPONSE,
            status.toReasonCode(),
            providerStatus = status,
        )
    }
    val contentType = header("Content-Type")?.substringBefore(';')?.trim()
    if (contentType != "text/event-stream") {
        close()
        throw protocolError(TtsFailure.SYNTHESIS, TtsStage.HTTP_RESPONSE, "UNEXPECTED_CONTENT_TYPE")
    }
    return body
}
