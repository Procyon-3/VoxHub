package name.me.voxhub.domain.provider

import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.domain.model.UsageAttribution
import name.me.voxhub.domain.model.UsageSource
import name.me.voxhub.domain.repository.MimoConfigRepository
import name.me.voxhub.domain.usage.UsageRecorder
import name.me.voxhub.provider.mimo.protocol.MIMO_API_ENDPOINT
import name.me.voxhub.provider.mimo.protocol.normalizeMimoEndpoint
import name.me.voxhub.provider.mimo.tts.MIMO_DESCRIPTOR
import name.me.voxhub.provider.mimo.tts.MIMO_VOICES
import name.me.voxhub.provider.mimo.tts.MimoProviderConfig
import name.me.voxhub.provider.mimo.tts.MimoProviderFactory

data class MimoProviderFormData(
    val displayName: String,
    val apiKey: String,
    val defaultVoiceId: String,
    val endpoint: String = MIMO_API_ENDPOINT,
)

class MimoProviderConfigService(
    private val configs: MimoConfigRepository,
    private val factory: MimoProviderFactory,
    private val usageRecorder: UsageRecorder,
) {
    suspend fun create(form: MimoProviderFormData, testState: ProviderTestState): ProviderConfigRecord {
        validate(form)
        return configs.create(
            form.displayName.trim(),
            form.defaultVoiceId,
            testState,
            MimoProviderConfig(form.apiKey.trim(), requireNotNull(normalizeMimoEndpoint(form.endpoint))),
        )
    }

    suspend fun update(
        existing: ProviderConfigRecord,
        form: MimoProviderFormData,
        testState: ProviderTestState,
    ): ProviderConfigRecord {
        require(existing.providerId == MIMO_DESCRIPTOR.id) { "Not a MiMo config" }
        validate(form)
        val updated = existing.copy(
            displayName = form.displayName.trim(),
            defaultVoiceId = form.defaultVoiceId,
            testState = testState,
        )
        configs.update(updated, MimoProviderConfig(form.apiKey.trim(), requireNotNull(normalizeMimoEndpoint(form.endpoint))))
        return updated
    }

    suspend fun delete(id: ProviderConfigId) = configs.delete(id)

    suspend fun testDraft(form: MimoProviderFormData): ProviderTestState {
        return testProvider(usageRecorder, UsageAttribution(MIMO_DESCRIPTOR.id, null, form.displayName.trim(), UsageSource.ACTIVE_TEST)) {
            validate(form)
            factory.create(MimoProviderConfig(form.apiKey.trim(), requireNotNull(normalizeMimoEndpoint(form.endpoint)))) to form.defaultVoiceId
        }
    }

    fun getConfig(id: ProviderConfigId): MimoProviderConfig? = configs.getConfig(id)

    private fun validate(form: MimoProviderFormData) {
        require(form.displayName.isNotBlank()) { "Display name is required" }
        require(form.apiKey.isNotBlank()) { "API key is required" }
        require(normalizeMimoEndpoint(form.endpoint) != null) { "Invalid API endpoint" }
        require(form.defaultVoiceId in MIMO_VOICES.map { it.id }) { "Unknown default voice" }
    }

}
