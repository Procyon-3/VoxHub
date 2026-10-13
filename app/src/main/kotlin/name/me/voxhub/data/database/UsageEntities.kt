package name.me.voxhub.data.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "synthesis_requests", indices = [Index("startedAt")])
data class UsageRequestEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val providerId: String?,
    val configId: String?,
    val configName: String,
    val source: String,
    val textCharacters: Int,
    val outcome: String = "RUNNING",
    val firstAudioMillis: Long? = null,
    val totalMillis: Long? = null,
    val failure: String? = null,
    val failureStage: String? = null,
)
