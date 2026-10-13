package name.me.voxhub.data.repository

import android.os.SystemClock
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import name.me.voxhub.data.database.UsageDatabase
import name.me.voxhub.data.database.UsageRequestEntity
import name.me.voxhub.domain.model.*

class UsageRepository(private val database: UsageDatabase) {
    private val dao = database.usageDao()
    private val maintenance = Mutex()
    private var initialized = false
    private var lastPruneElapsedMillis = 0L
    val changes = dao.changes()

    suspend fun initialize() = maintenance.withLock {
        if (!initialized) {
            database.withWriteTransaction {
                dao.prune(System.currentTimeMillis() - USAGE_RETENTION_MILLIS)
                dao.interruptUnfinished()
            }
            lastPruneElapsedMillis = SystemClock.elapsedRealtime()
            initialized = true
        }
    }

    suspend fun begin(id: String, startedAt: Long, attribution: UsageAttribution, characters: Int) {
        initialize()
        maintenance.withLock {
            val now = SystemClock.elapsedRealtime()
            val pruneDue = now - lastPruneElapsedMillis >= PRUNE_INTERVAL_MILLIS
            database.withWriteTransaction {
                if (pruneDue) dao.prune(System.currentTimeMillis() - USAGE_RETENTION_MILLIS)
                dao.insert(UsageRequestEntity(id, startedAt, attribution.providerId, attribution.configId,
                    attribution.configName, attribution.source.name, characters))
            }
            if (pruneDue) lastPruneElapsedMillis = now
        }
    }

    // UPDATE, never upsert: a cleared or expired in-flight request cannot reappear.
    suspend fun finish(id: String, outcome: UsageOutcome, firstAudioMillis: Long?, totalMillis: Long,
        failure: String?, stage: String?) = dao.finish(id, outcome.name, firstAudioMillis, totalMillis, failure, stage)

    // Explicit maintenance (also used by the debug usage probe); regular requests prune at most daily.
    suspend fun prune() {
        initialize()
        maintenance.withLock {
            dao.prune(System.currentTimeMillis() - USAGE_RETENTION_MILLIS)
            lastPruneElapsedMillis = SystemClock.elapsedRealtime()
        }
    }

    suspend fun snapshot(filter: UsageFilter, limit: Int): UsageSnapshot {
        initialize()
        val cutoff = System.currentTimeMillis() - USAGE_RETENTION_MILLIS
        val since = maxOf(filter.since, cutoff)
        val config = filter.configuration
        return database.withReadTransaction {
            val overview = dao.overview(since, filter.until, config != null, config?.providerId,
                config?.configId, config?.configName, filter.source?.name)
            val entries = dao.entries(since, filter.until, config != null, config?.providerId,
                config?.configId, config?.configName, filter.source?.name, limit).map {
                UsageEntry(it.id, it.startedAt, UsageAttribution(it.providerId, it.configId, it.configName,
                    UsageSource.valueOf(it.source)), it.textCharacters, UsageOutcome.valueOf(it.outcome),
                    it.firstAudioMillis, it.totalMillis, it.failure, it.failureStage)
            }
            UsageSnapshot(overview, entries, dao.configurations(cutoff))
        }
    }

    suspend fun clear() { initialize(); dao.clear() }

    private companion object {
        const val PRUNE_INTERVAL_MILLIS = 24L * 60 * 60 * 1_000
    }
}
