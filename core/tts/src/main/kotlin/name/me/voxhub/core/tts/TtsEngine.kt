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
                    if (count == -1) break
                    sink.write(buffer, 0, count)
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
        if (provider.voices.none { it.locale == request.locale }) {
            throw invalidRequest("UNSUPPORTED_LOCALE")
        }
        if (provider.voices.none { it.id == request.voiceId }) {
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
