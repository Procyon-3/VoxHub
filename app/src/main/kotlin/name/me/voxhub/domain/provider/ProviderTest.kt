package name.me.voxhub.domain.provider

import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import name.me.voxhub.core.tts.AudioSink
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.TtsDiagnostic
import name.me.voxhub.core.tts.TtsEngine
import name.me.voxhub.core.tts.TtsError
import name.me.voxhub.core.tts.TtsProvider
import name.me.voxhub.core.tts.TtsRequest
import name.me.voxhub.core.tts.TtsStage
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.UsageAttribution
import name.me.voxhub.domain.model.toSanitizedReason
import name.me.voxhub.domain.usage.UsageRecorder

internal class ProviderTestAudioSink : AudioSink {
    var format: PcmFormat? = null
        private set
    var totalBytes: Long = 0
        private set
    var completed = false
        private set
    var error: TtsError? = null
        private set

    override suspend fun start(format: PcmFormat) {
        check(this.format == null && !completed && error == null)
        this.format = format
    }

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        check(format != null && !completed && error == null)
        totalBytes += length
    }

    override suspend fun complete() {
        check(format != null && error == null)
        if (totalBytes == 0L || totalBytes % requireNotNull(format).bytesPerFrame != 0L) {
            throw name.me.voxhub.core.tts.TtsException(name.me.voxhub.core.tts.TtsFailure.SYNTHESIS,
                TtsDiagnostic(stage = TtsStage.STREAM_PARSE, reasonCode = "EMPTY_OR_INVALID_AUDIO"))
        }
        completed = true
    }

    override suspend fun fail(error: TtsError) {
        check(!completed && this.error == null)
        this.error = error
    }
}

internal suspend fun testProvider(
    usageRecorder: UsageRecorder,
    attribution: UsageAttribution,
    resolve: suspend () -> Pair<TtsProvider, String>,
): ProviderTestState = withContext(Dispatchers.IO) {
    Timber.i("Provider test started")
    val testedAt = System.currentTimeMillis()
    val sink = ProviderTestAudioSink()
    try {
        usageRecorder.record(attribution, "你好，Hi。", sink) { observed ->
            val (provider, voiceId) = try { resolve() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                throw name.me.voxhub.core.tts.TtsException(name.me.voxhub.core.tts.TtsFailure.INVALID_REQUEST,
                    TtsDiagnostic(stage = TtsStage.REQUEST_VALIDATION, reasonCode = "INVALID_CONFIGURATION"), failure)
            }
            val voice = provider.voices.first { it.id == voiceId }
            TtsEngine(provider).synthesize(TtsRequest("你好，Hi。", voice.locale, voiceId), observed)
        }
        val error = sink.error
        if (error != null) {
            Timber.e("Provider test failed failure=%s diagnostic=%s", error.failure, error.diagnostic)
            ProviderTestState.Failure(testedAt, error.diagnostic?.toSanitizedReason() ?: name.me.voxhub.domain.model.SanitizedReason())
        } else {
            Timber.i("Provider test succeeded")
            ProviderTestState.Success(testedAt)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Timber.e(failure, "Provider test failed")
        ProviderTestState.Failure(testedAt,
            ((failure as? name.me.voxhub.core.tts.TtsException)?.error?.diagnostic
                ?: TtsDiagnostic(stage = TtsStage.STREAM_READ, reasonCode = "UNEXPECTED_ERROR")).toSanitizedReason())
    }
}
