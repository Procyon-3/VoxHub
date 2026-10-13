package name.me.voxhub.domain.repository

import name.me.voxhub.domain.model.MimoConfigAggregate
import name.me.voxhub.domain.model.ProviderConfigAggregate
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.VoiceCloneConfigAggregate
import name.me.voxhub.domain.model.VoiceCloneStoredAggregate
import name.me.voxhub.domain.model.VoiceDesignConfigAggregate
import name.me.voxhub.provider.mimo.tts.MimoProviderConfig

interface MimoConfigRepository {
    fun getAggregate(id: ProviderConfigId): MimoConfigAggregate?
    fun getConfig(id: ProviderConfigId): MimoProviderConfig? = getAggregate(id)?.config

    suspend fun create(
        displayName: String,
        defaultVoiceId: String,
        testState: ProviderTestState,
        config: MimoProviderConfig,
    ): ProviderConfigRecord

    suspend fun update(record: ProviderConfigRecord, config: MimoProviderConfig)
    suspend fun delete(id: ProviderConfigId)
}

interface SnapshotConfigRepository {
    suspend fun getSnapshotAggregate(id: ProviderConfigId): ProviderConfigAggregate?
    fun hasCompleteConfig(id: ProviderConfigId): Boolean
}

interface VoiceCloneConfigRepository {
    fun getCloneStored(id: ProviderConfigId): VoiceCloneStoredAggregate?
    suspend fun getCloneAggregate(id: ProviderConfigId): VoiceCloneConfigAggregate?
    suspend fun saveClone(existing: ProviderConfigRecord?, displayName: String, audioName: String,
        config: name.me.voxhub.provider.mimo.voiceclone.MimoVoiceCloneProviderConfig,
        testState: ProviderTestState): ProviderConfigRecord
    suspend fun delete(id: ProviderConfigId)
}

interface VoiceDesignConfigRepository {
    fun getDesignAggregate(id: ProviderConfigId): VoiceDesignConfigAggregate?
    suspend fun createDesign(displayName: String, testState: ProviderTestState,
        config: name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderConfig): ProviderConfigRecord
    suspend fun updateDesign(record: ProviderConfigRecord,
        config: name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderConfig)
    suspend fun delete(id: ProviderConfigId)
}
