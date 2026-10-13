package name.me.voxhub.core.tts

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

class TtsEngine(
    private val provider: TtsProvider,
) {
    private val outputFormat = provider.descriptor.outputFormat
    suspend fun synthesize(
        request: TtsRequest,
        sink: AudioSink,
    ) {
        try {
            validate(request)
            if (request.text.isEmpty()) {
                sink.start(outputFormat)
                sink.complete()
                return
            }

            provider.openPcmStream(request).use { stream ->
                if (stream.format != outputFormat) {
                    throw TtsException(
                        TtsFailure.SYNTHESIS,
                        TtsDiagnostic(stage = TtsStage.STREAM_PARSE, reasonCode = "PCM_FORMAT_MISMATCH"),
                    )
                }

                sink.start(outputFormat)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = stream.read(buffer)
                    when {
                        count == -1 -> break
                        count == 0 -> throw TtsException(
                            TtsFailure.SYNTHESIS,
                            TtsDiagnostic(stage = TtsStage.STREAM_READ, reasonCode = "ZERO_LENGTH_READ"),
                        )
                        count < -1 || count > buffer.size -> throw TtsException(
                            TtsFailure.SYNTHESIS,
                            TtsDiagnostic(stage = TtsStage.STREAM_READ, reasonCode = "INVALID_READ_COUNT"),
                        )
                        else -> sink.write(buffer, 0, count)
                    }
                }
            }
            sink.complete()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            sink.fail(
                (error as? TtsException)?.error ?: TtsError(
                    TtsFailure.SYNTHESIS,
                    TtsDiagnostic(stage = TtsStage.STREAM_READ, reasonCode = "UNEXPECTED_ERROR"),
                ),
            )
        }
    }

    private fun validate(request: TtsRequest) {
        if (request.locale !in provider.voices.map { it.locale }.toSet()) {
            throw invalidRequest("UNSUPPORTED_LOCALE")
        }
        if (request.voiceId !in provider.voices.map { it.id }.toSet()) {
            throw invalidRequest("UNKNOWN_VOICE")
        }
    }

    private fun invalidRequest(reasonCode: String) = TtsException(
        TtsFailure.INVALID_REQUEST,
        TtsDiagnostic(stage = TtsStage.REQUEST_VALIDATION, reasonCode = reasonCode),
    )

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
