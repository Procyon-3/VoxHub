package name.me.voxhub.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import name.me.voxhub.domain.model.UsageConfiguration
import name.me.voxhub.domain.model.UsageOverview

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(request: UsageRequestEntity)

    @Query("UPDATE synthesis_requests SET outcome=:outcome, firstAudioMillis=:firstAudioMillis, totalMillis=:totalMillis, failure=:failure, failureStage=:failureStage WHERE id=:id AND outcome='RUNNING'")
    suspend fun finish(id: String, outcome: String, firstAudioMillis: Long?, totalMillis: Long, failure: String?, failureStage: String?)

    @Query("UPDATE synthesis_requests SET outcome='INTERRUPTED' WHERE outcome='RUNNING'")
    suspend fun interruptUnfinished()

    @Query("DELETE FROM synthesis_requests WHERE startedAt < :cutoff")
    suspend fun prune(cutoff: Long)

    @Query("DELETE FROM synthesis_requests")
    suspend fun clear()

    // Room invalidates this query on updates as well as inserts/deletes. Do not distinctUntilChanged.
    @Query("SELECT COUNT(*) FROM synthesis_requests")
    fun changes(): Flow<Long>

    @Query("""
        SELECT COUNT(*) AS count, COALESCE(SUM(textCharacters), 0) AS textCharacters,
        COALESCE(SUM(outcome='SUCCEEDED'), 0) AS succeeded,
        COALESCE(SUM(outcome='FAILED'), 0) AS failed,
        COALESCE(SUM(outcome='CANCELLED'), 0) AS cancelled,
        COALESCE(SUM(outcome='RUNNING'), 0) AS running,
        COALESCE(SUM(outcome='INTERRUPTED'), 0) AS interrupted,
        AVG(CASE WHEN outcome='SUCCEEDED' THEN firstAudioMillis END) AS averageFirstAudioMillis,
        AVG(CASE WHEN outcome='SUCCEEDED' THEN totalMillis END) AS averageTotalMillis
        FROM synthesis_requests WHERE startedAt >= :since AND startedAt < :until AND (:filterConfig = 0 OR (providerId IS :providerId AND configId IS :configId AND (configId IS NOT NULL OR configName = :configName))) AND (:source IS NULL OR source = :source)
    """)
    suspend fun overview(since: Long, until: Long, filterConfig: Boolean, providerId: String?, configId: String?, configName: String?, source: String?): UsageOverview

    @Query("SELECT * FROM synthesis_requests WHERE startedAt >= :since AND startedAt < :until AND (:filterConfig = 0 OR (providerId IS :providerId AND configId IS :configId AND (configId IS NOT NULL OR configName = :configName))) AND (:source IS NULL OR source = :source) ORDER BY startedAt DESC, id DESC LIMIT :limit")
    suspend fun entries(since: Long, until: Long, filterConfig: Boolean, providerId: String?, configId: String?, configName: String?, source: String?, limit: Int): List<UsageRequestEntity>

    @Query("""SELECT r.providerId, r.configId, r.configName FROM synthesis_requests r
        WHERE r.startedAt >= :cutoff AND NOT EXISTS (
            SELECT 1 FROM synthesis_requests newer WHERE newer.providerId IS r.providerId
            AND newer.configId IS r.configId AND (r.configId IS NOT NULL OR newer.configName=r.configName)
            AND (newer.startedAt > r.startedAt OR (newer.startedAt=r.startedAt AND newer.id > r.id)))
        ORDER BY r.startedAt DESC""")
    suspend fun configurations(cutoff: Long): List<UsageConfiguration>
}
