package name.me.voxhub.domain.provider

import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.UsageAttribution
import name.me.voxhub.domain.model.UsageSource
import name.me.voxhub.domain.repository.VoiceCloneConfigRepository
import name.me.voxhub.domain.usage.UsageRecorder
import name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT
import name.me.voxhub.provider.mimo.protocol.normalizeMimoEndpoint
import name.me.voxhub.provider.mimo.voiceclone.MimoVoiceCloneProviderConfig
import name.me.voxhub.provider.mimo.voiceclone.MimoVoiceCloneProviderFactory
import name.me.voxhub.provider.mimo.voiceclone.ReferenceAudio
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DESCRIPTOR
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_VOICES

class VoiceCloneProviderRegistration(private val factory: MimoVoiceCloneProviderFactory) : ProviderRegistration {
    override val descriptor = VOICECLONE_DESCRIPTOR
    override val voices = VOICECLONE_VOICES
    override val defaultVoiceId = VOICECLONE_DEFAULT_VOICE_ID
    internal fun createProvider(config: MimoVoiceCloneProviderConfig) = factory.create(config)
}

class VoiceCloneProviderFormData(val displayName: String, val apiKey: String, val audio: ReferenceAudio,
    val audioName: String, val styleInstruction: String, val endpoint: String = MIMO_API_ENDPOINT)

class VoiceCloneProviderConfigService(
    private val configs: VoiceCloneConfigRepository,
    private val factory: MimoVoiceCloneProviderFactory,
    private val usageRecorder: UsageRecorder,
) {
    private fun config(form: VoiceCloneProviderFormData): MimoVoiceCloneProviderConfig {
        require(form.displayName.isNotBlank() && form.apiKey.isNotBlank())
        return MimoVoiceCloneProviderConfig(form.apiKey.trim(), form.audio, form.styleInstruction,
            requireNotNull(normalizeMimoEndpoint(form.endpoint)) { "Invalid API endpoint" })
    }
    suspend fun save(existing: ProviderConfigRecord?, form: VoiceCloneProviderFormData, state: ProviderTestState) =
        configs.saveClone(existing, form.displayName, form.audioName, config(form), state)
    fun getStored(id: ProviderConfigId) = configs.getCloneStored(id)
    suspend fun load(id: ProviderConfigId) = configs.getCloneAggregate(id)
    suspend fun testDraft(form: VoiceCloneProviderFormData) =
        testProvider(usageRecorder, UsageAttribution(VOICECLONE_DESCRIPTOR.id, null, form.displayName.trim(), UsageSource.ACTIVE_TEST)) {
            factory.create(config(form)) to VOICECLONE_DEFAULT_VOICE_ID
        }
    suspend fun delete(id: ProviderConfigId) = configs.delete(id)
}
