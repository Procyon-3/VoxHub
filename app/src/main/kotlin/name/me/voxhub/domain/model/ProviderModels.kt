package name.me.voxhub.domain.model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ProviderConfigId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider config id must not be blank" }
    }
}

@Serializable
data class ProviderConfigRecord(
    val id: ProviderConfigId,
    val providerId: String,
    val displayName: String,
    val defaultVoiceId: String,
    val testState: ProviderTestState,
)

@Serializable
sealed interface ProviderTestState {
    @Serializable
    data object NotTested : ProviderTestState

    @Serializable
    data class Success(val testedAtEpochMillis: Long) : ProviderTestState

    @Serializable
    data class Failure(
        val testedAtEpochMillis: Long,
        val reason: SanitizedReason,
    ) : ProviderTestState
}
