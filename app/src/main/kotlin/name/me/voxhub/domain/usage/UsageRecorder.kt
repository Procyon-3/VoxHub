package name.me.voxhub.domain.usage

import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import name.me.voxhub.core.tts.*
import name.me.voxhub.data.repository.UsageRepository
import name.me.voxhub.domain.model.*
import timber.log.Timber

class UsageRecorder(private val repository: UsageRepository) {
    /** The block includes configuration resolution so validation failures are counted as requests. */
    suspend fun record(attribution: UsageAttribution, text: String, sink: AudioSink,
        startedAt: Long = System.currentTimeMillis(),
        startedElapsed: Long = SystemClock.elapsedRealtime(),
        block: suspend (AudioSink) -> Unit) {
        val id = UUID.randomUUID().toString()
        val measurement = UsageMeasurement { SystemClock.elapsedRealtime() - startedElapsed }
        withContext(NonCancellable + Dispatchers.IO) {
            try { repository.begin(id, startedAt, attribution, text.codePointCount(0, text.length)) }
            catch (failure: Exception) { Timber.e(failure, "Usage request start write failed") }
        }
        Timber.d("Synthesis request=%s provider=%s source=%s textLength=%d", id, attribution.providerId, attribution.source, text.length)
        val observed = object : AudioSink {
            override suspend fun start(format: PcmFormat) = sink.start(format)
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                sink.write(bytes, offset, length)
                if (length > 0) measurement.audio()
            }
            override suspend fun complete() { sink.complete(); measurement.complete() }
            override suspend fun fail(error: TtsError) { measurement.fail(error); sink.fail(error) }
        }
        try {
            block(observed)
        } catch (cancelled: CancellationException) {
            measurement.cancel()
            throw cancelled
        } catch (failure: Exception) {
            measurement.fail((failure as? TtsException)?.error ?: TtsError(TtsFailure.SERVICE))
            throw failure
        } finally {
            val duration = SystemClock.elapsedRealtime() - startedElapsed
            withContext(NonCancellable + Dispatchers.IO) {
                try { repository.finish(id, measurement.outcome, measurement.firstAudioMillis, duration,
                    measurement.error?.failure?.name, measurement.error?.diagnostic?.stage?.name) }
                catch (failure: Exception) { Timber.e(failure, "Usage request finish write failed") }
            }
            Timber.d("Synthesis request=%s outcome=%s durationMs=%d", id, measurement.outcome, duration)
        }
    }
}

internal class UsageMeasurement(private val elapsedMillis: () -> Long) {
    var outcome = UsageOutcome.CANCELLED
        private set
    var firstAudioMillis: Long? = null
        private set
    var error: TtsError? = null
        private set
    fun audio() { if (firstAudioMillis == null) firstAudioMillis = elapsedMillis() }
    fun complete() { outcome = UsageOutcome.SUCCEEDED }
    fun fail(value: TtsError) {
        outcome = if (value.failure == TtsFailure.CANCELLED) UsageOutcome.CANCELLED else UsageOutcome.FAILED
        error = value.takeUnless { outcome == UsageOutcome.CANCELLED }
    }
    fun cancel() { outcome = UsageOutcome.CANCELLED; error = null }
}
