package name.me.voxhub.usage

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import name.me.voxhub.core.tts.*
import name.me.voxhub.data.database.UsageDatabase
import name.me.voxhub.data.database.UsageRequestEntity
import name.me.voxhub.data.repository.UsageRepository
import name.me.voxhub.domain.model.*
import name.me.voxhub.domain.usage.UsageRecorder

/** Exercises the generated Room implementation and real recorder without network or user data. */
internal suspend fun runUsageProbe(context: Context): String = coroutineScope {
    val database = Room.inMemoryDatabaseBuilder<UsageDatabase>(context)
        .setDriver(AndroidSQLiteDriver()).build()
    try {
        val dao = database.usageDao()
        val now = System.currentTimeMillis()
        val attribution = UsageAttribution("test", "config", "Test", UsageSource.SYSTEM_TTS)
        val repository = UsageRepository(database)
        suspend fun snapshot(config: UsageConfiguration? = null, source: UsageSource? = null) =
            repository.snapshot(UsageFilter(0, Long.MAX_VALUE, config, source), 100)
        dao.insert(UsageRequestEntity("expired", now - USAGE_RETENTION_MILLIS - 1, "test", "config", "Test", "SYSTEM_TTS", 5))
        dao.insert(UsageRequestEntity("interrupted", now, "test", "config", "Test", "SYSTEM_TTS", 5))
        repository.initialize()
        check(dao.changes().first() == 1L)
        check(snapshot().entries.single().outcome == UsageOutcome.INTERRUPTED)
        check(snapshot().entries.single().totalMillis == null)
        val recorder = UsageRecorder(repository)
        val format = PcmFormat(24000, 1, PcmEncoding.S16_LE)
        val sink = object : AudioSink {
            override suspend fun start(format: PcmFormat) {}
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) { delay(10) }
            override suspend fun complete() {}
            override suspend fun fail(error: TtsError) {}
        }
        // Multiple chunks remain one request; Unicode code points, whitespace and punctuation count.
        recorder.record(attribution, "你😀 。", sink) { observed ->
            check(snapshot().entries.count { it.outcome == UsageOutcome.RUNNING } == 1)
            observed.start(format)
            observed.write(byteArrayOf(0, 0), 0, 2)
            observed.write(byteArrayOf(0, 0), 0, 2)
            observed.complete()
        }
        val success = snapshot().entries.first { it.outcome == UsageOutcome.SUCCEEDED }
        check(success.textCharacters == 4)
        check(success.firstAudioMillis!! >= 10 && success.totalMillis!! >= success.firstAudioMillis)
        recorder.record(attribution, "failure", sink) { it.fail(TtsError(TtsFailure.NETWORK, TtsDiagnostic(stage = TtsStage.STREAM_READ))) }
        try {
            recorder.record(attribution, "invalid", sink) {
                throw TtsException(TtsFailure.INVALID_REQUEST, TtsDiagnostic(stage = TtsStage.REQUEST_VALIDATION))
            }
        } catch (_: TtsException) {}
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            recorder.record(attribution, "cancel", sink) { entered.complete(Unit); awaitCancellation() }
        }
        entered.await(); job.cancelAndJoin()
        var overview = snapshot().overview
        check(overview.count == 5L && overview.succeeded == 1L && overview.failed == 2L && overview.cancelled == 1L && overview.interrupted == 1L)
        check(overview.successRate == 1.0 / 3.0)
        check(overview.averageFirstAudioMillis == success.firstAudioMillis.toDouble())
        check(overview.averageTotalMillis == success.totalMillis.toDouble())
        check(snapshot().entries.first { it.failure == "INVALID_REQUEST" }.failureStage == "REQUEST_VALIDATION")
        check(snapshot(source = UsageSource.ACTIVE_TEST).overview.count == 0L)
        check(snapshot(UsageConfiguration("test", "config", "Renamed")).overview.count == 5L)
        recorder.record(attribution.copy(configId = null, configName = "Draft A", source = UsageSource.ACTIVE_TEST), "test", sink) { it.complete() }
        recorder.record(attribution.copy(configId = null, configName = "Draft B", source = UsageSource.ACTIVE_TEST), "test", sink) { it.complete() }
        check(snapshot(UsageConfiguration("test", null, "Draft A")).overview.count == 1L)
        check(snapshot(source = UsageSource.ACTIVE_TEST).overview.count == 2L)
        repository.begin("live", now, attribution, 1)
        val observing = CompletableDeferred<Unit>()
        // Updates must trigger the query even though the row count is unchanged.
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            repository.changes.first {
                val result = snapshot().entries.first { entry -> entry.id == "live" }
                observing.complete(Unit)
                result.outcome == UsageOutcome.FAILED
            }
        }
        observing.await()
        repository.finish("live", UsageOutcome.FAILED, null, 10, "NETWORK", "STREAM_READ")
        withTimeout(5000) { update.await() }
        repository.begin("clear-in-flight", now, attribution, 1)
        repository.clear()
        repository.finish("clear-in-flight", UsageOutcome.SUCCEEDED, 1, 2, null, null)
        check(snapshot().overview.count == 0L)
        repository.begin("after-clear", now, attribution, 1)
        check(snapshot().overview.running == 1L)
        dao.insert(UsageRequestEntity("expired-again", now - USAGE_RETENTION_MILLIS - 1000, null, null, "", "SYSTEM_TTS", 1))
        repository.prune()
        check(snapshot().overview.count == 1L)
        check(dao.changes().first() == 1L)
        "PASS: lifecycle, Unicode, timing, failures, cancellation, retention, interruption, filters, aggregates, update notification, clear race"
    } finally { database.close() }
}
