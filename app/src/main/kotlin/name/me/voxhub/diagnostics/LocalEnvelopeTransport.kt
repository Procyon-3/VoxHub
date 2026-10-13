package name.me.voxhub.diagnostics

import io.sentry.Hint
import io.sentry.SentryOptions
import io.sentry.SentryEnvelope
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import io.sentry.util.HintUtils
import java.io.IOException
import java.util.UUID

/** Terminal local transport. No HTTP client, DNS lookup, or network fallback exists. */
internal class LocalEnvelopeTransport(
    private val store: DiagnosticStore,
    private val options: SentryOptions,
) : ITransport {
    private var closed = false
    @Volatile private var healthy = true

    override fun send(envelope: SentryEnvelope, hint: Hint) = synchronized(store.lock) {
        if (closed) throw IOException("Local diagnostic transport is closed")
        val sdkHint = HintUtils.getSentrySdkHint(hint)
        try {
            store.saveReport(envelope.header.eventId?.toString() ?: UUID.randomUUID().toString()) {
                options.serializer.serialize(envelope, it)
            }
            // Let the SDK maintain its startup-crash and last-reported-ANR markers.
            // The archive is already durable; the SDK's transient copy is now redundant.
            options.envelopeDiskCache.storeEnvelope(envelope, hint)
            options.envelopeDiskCache.discard(envelope)
            (sdkHint as? DiskFlushNotification)?.let {
                if (it.isFlushable(envelope.header.eventId)) it.markFlushed()
            }
            (sdkHint as? Retryable)?.setRetry(false)
            (sdkHint as? SubmissionResult)?.setResult(true)
        } catch (failure: Exception) {
            (sdkHint as? Retryable)?.setRetry(true)
            (sdkHint as? SubmissionResult)?.setResult(false)
            healthy = false
            throw IOException("Local diagnostic report could not be saved", failure)
        }
        Unit
    }

    override fun isHealthy() = healthy

    // send() commits synchronously, including for fatal exceptions.
    override fun flush(timeoutMillis: Long) = Unit
    override fun close() { synchronized(store.lock) { closed = true } }
    override fun close(isRestarting: Boolean) = close()
    override fun getRateLimiter(): RateLimiter? = null
}
