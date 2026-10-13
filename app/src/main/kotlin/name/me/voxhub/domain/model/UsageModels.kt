package name.me.voxhub.domain.model

const val USAGE_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

enum class UsageSource { SYSTEM_TTS, PLAYGROUND, ACTIVE_TEST }
enum class UsageOutcome { SUCCEEDED, FAILED, CANCELLED, RUNNING, INTERRUPTED }

data class UsageAttribution(
    val providerId: String?,
    val configId: String?,
    val configName: String,
    val source: UsageSource,
)

data class UsageEntry(
    val id: String,
    val startedAt: Long,
    val attribution: UsageAttribution,
    val textCharacters: Int,
    val outcome: UsageOutcome,
    val firstAudioMillis: Long?,
    val totalMillis: Long?,
    val failure: String?,
    val failureStage: String?,
)

data class UsageOverview(
    val count: Long = 0,
    val textCharacters: Long = 0,
    val succeeded: Long = 0,
    val failed: Long = 0,
    val cancelled: Long = 0,
    val running: Long = 0,
    val interrupted: Long = 0,
    val averageFirstAudioMillis: Double? = null,
    val averageTotalMillis: Double? = null,
) {
    val successRate: Double? get() = (succeeded + failed).takeIf { it > 0 }?.let { succeeded.toDouble() / it }
}

/** Null config IDs denote unsaved tests or requests without a configuration. */
data class UsageConfiguration(val providerId: String?, val configId: String?, val configName: String)
data class UsageFilter(
    val since: Long,
    val until: Long,
    val configuration: UsageConfiguration? = null,
    val source: UsageSource? = null,
)
data class UsageSnapshot(val overview: UsageOverview, val entries: List<UsageEntry>, val configurations: List<UsageConfiguration>)
