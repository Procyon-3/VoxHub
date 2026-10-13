package name.me.voxhub.domain.provider

import name.me.voxhub.core.tts.TtsProvider
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.UsageAttribution
import name.me.voxhub.domain.model.UsageSource
import name.me.voxhub.domain.repository.VoiceDesignConfigRepository
import name.me.voxhub.domain.usage.UsageRecorder
import name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT
import name.me.voxhub.provider.mimo.protocol.normalizeMimoEndpoint
import name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderConfig
import name.me.voxhub.provider.mimo.voicedesign.MimoVoiceDesignProviderFactory
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DESCRIPTOR
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_VOICES

class VoiceDesignProviderRegistration(
    private val factory: MimoVoiceDesignProviderFactory,
) : ProviderRegistration {
    override val descriptor = VOICEDESIGN_DESCRIPTOR
    override val voices = VOICEDESIGN_VOICES
    override val defaultVoiceId = VOICEDESIGN_DEFAULT_VOICE_ID
    internal fun createProvider(config: MimoVoiceDesignProviderConfig): TtsProvider = factory.create(config)
}

data class VoiceDesignProviderFormData(val displayName: String, val apiKey: String, val voiceDesignPrompt: String,
    val endpoint: String = MIMO_API_ENDPOINT)

class VoiceDesignProviderConfigService(
    private val configs: VoiceDesignConfigRepository,
    private val factory: MimoVoiceDesignProviderFactory,
    private val usageRecorder: UsageRecorder,
) {
    private fun config(form: VoiceDesignProviderFormData): MimoVoiceDesignProviderConfig {
        require(form.displayName.isNotBlank() && form.apiKey.isNotBlank() && form.voiceDesignPrompt.isNotBlank())
        return MimoVoiceDesignProviderConfig(form.apiKey.trim(), form.voiceDesignPrompt,
            requireNotNull(normalizeMimoEndpoint(form.endpoint)) { "Invalid API endpoint" })
    }
    suspend fun create(form: VoiceDesignProviderFormData, testState: ProviderTestState): ProviderConfigRecord =
        configs.createDesign(form.displayName.trim(), testState, config(form))

    suspend fun update(existing: ProviderConfigRecord, form: VoiceDesignProviderFormData, testState: ProviderTestState): ProviderConfigRecord {
        require(existing.providerId == VOICEDESIGN_DESCRIPTOR.id)
        val updated = existing.copy(displayName = form.displayName.trim(), defaultVoiceId = VOICEDESIGN_DEFAULT_VOICE_ID, testState = testState)
        configs.updateDesign(updated, config(form))
        return updated
    }
    fun getConfig(id: ProviderConfigId): MimoVoiceDesignProviderConfig? = configs.getDesignAggregate(id)?.config
    suspend fun testDraft(form: VoiceDesignProviderFormData): ProviderTestState =
        testProvider(usageRecorder, UsageAttribution(VOICEDESIGN_DESCRIPTOR.id, null, form.displayName.trim(), UsageSource.ACTIVE_TEST)) {
            factory.create(config(form)) to VOICEDESIGN_DEFAULT_VOICE_ID
        }
    suspend fun delete(id: ProviderConfigId) = configs.delete(id)
}
