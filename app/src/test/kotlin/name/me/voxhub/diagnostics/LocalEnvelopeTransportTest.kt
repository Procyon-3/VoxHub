package name.me.voxhub.diagnostics

import io.sentry.JsonSerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.Retryable
import io.sentry.hints.SubmissionResult
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalEnvelopeTransportTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Result : DiskFlushNotification, Retryable, SubmissionResult {
        var flushed = false
        var shouldRetry = false
        var submitted = false
        override fun markFlushed() { flushed = true }
        override fun isFlushable(eventId: SentryId?) = true
        override fun setFlushable(eventId: SentryId) = Unit
        override fun setRetry(retry: Boolean) { this.shouldRetry = retry }
        override fun isRetry() = shouldRetry
        override fun setResult(success: Boolean) { submitted = success }
        override fun isSuccess() = submitted
    }

    @Test fun acknowledgesOnlyAfterDurableReadableEnvelopeExists() {
        val store = DiagnosticStore(temporary.newFolder())
        val options = SentryOptions()
        val serializer = JsonSerializer(options)
        options.setSerializer(serializer)
        val envelope = SentryEnvelope.from(serializer, SentryEvent(), null)
        val result = Result()
        LocalEnvelopeTransport(store, options).send(envelope, HintUtils.createWithTypeCheckHint(result))
        assertTrue(result.flushed)
        assertTrue(result.submitted)
        assertFalse(result.shouldRetry)
        val restored = store.reports.listFiles()!!.single().inputStream().use(serializer::deserializeEnvelope)
        assertEquals(envelope.header.eventId, restored!!.header.eventId)
    }

    @Test fun storageFailureRequestsRetryAndDoesNotAcknowledge() {
        val root = temporary.newFolder()
        File(root, "logs").writeText("blocks directory creation")
        val store = DiagnosticStore(root)
        val options = SentryOptions()
        val serializer = JsonSerializer(options)
        options.setSerializer(serializer)
        val result = Result()
        try {
            LocalEnvelopeTransport(store, options).send(
                SentryEnvelope.from(serializer, SentryEvent(), null), HintUtils.createWithTypeCheckHint(result))
            fail("Expected failure")
        } catch (_: IOException) { }
        assertFalse(result.flushed)
        assertFalse(result.submitted)
        assertTrue(result.shouldRetry)
    }
}
