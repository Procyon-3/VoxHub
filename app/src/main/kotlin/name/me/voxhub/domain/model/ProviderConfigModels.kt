package name.me.voxhub.domain.model

import name.me.voxhub.provider.mimo.tts.MimoProviderConfig

data class ProviderStateDefinition(
    val providerId: String,
    val voiceIds: Set<String>,
)

data class MimoConfigAggregate(
    override val record: ProviderConfigRecord,
    val config: MimoProviderConfig,
) : ProviderConfigAggregate

sealed interface ProviderConfigAggregate {
    val record: ProviderConfigRecord
}

data class VoiceDesignConfigAggregate(
    override val record: ProviderConfigRecord,
    val config: name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderConfig,
) : ProviderConfigAggregate

class VoiceCloneStoredConfig(
    val apiKey: String,
    val audioVersion: String,
    val mimeType: String,
    val audioName: String,
    val audioSize: Int,
    val styleInstruction: String,
    val endpoint: String,
)

data class VoiceCloneStoredAggregate(val record: ProviderConfigRecord, val config: VoiceCloneStoredConfig)

data class VoiceCloneConfigAggregate(
    override val record: ProviderConfigRecord,
    val config: name.me.voxhub.provider.mimo.voiceclone.MimoVoiceCloneProviderConfig,
    val stored: VoiceCloneStoredAggregate,
) : ProviderConfigAggregate
