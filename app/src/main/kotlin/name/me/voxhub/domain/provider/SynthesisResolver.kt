package name.me.voxhub.domain.provider

import name.me.voxhub.domain.model.MimoConfigAggregate
import name.me.voxhub.domain.model.ProviderConfigAggregate
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderSnapshot
import name.me.voxhub.domain.model.VoiceCloneConfigAggregate
import name.me.voxhub.domain.model.VoiceDesignConfigAggregate
import name.me.voxhub.domain.repository.SnapshotConfigRepository

class SynthesisResolver(
    private val configs: SnapshotConfigRepository,
    private val catalog: ProviderCatalog,
) {
    fun hasCompleteConfig(configId: ProviderConfigId): Boolean = configs.hasCompleteConfig(configId)

    suspend fun resolve(configId: ProviderConfigId): ProviderSnapshot? {
        val aggregate = configs.getSnapshotAggregate(configId) ?: return null
        return resolveAggregate(aggregate)
    }

    private fun resolveAggregate(aggregate: ProviderConfigAggregate): ProviderSnapshot? {
        val record = aggregate.record
        val registration = catalog[record.providerId] ?: return null
        val defaultVoice = registration.voices.firstOrNull { it.id == record.defaultVoiceId } ?: return null
        val provider = when (aggregate) {
            is MimoConfigAggregate -> (registration as? MimoProviderRegistration)?.createProvider(aggregate.config)
            is VoiceDesignConfigAggregate -> (registration as? VoiceDesignProviderRegistration)?.createProvider(aggregate.config)
            is VoiceCloneConfigAggregate -> (registration as? VoiceCloneProviderRegistration)?.createProvider(aggregate.config)
        } ?: return null
        return ProviderSnapshot(
            configId = record.id,
            providerId = record.providerId,
            displayName = record.displayName,
            provider = provider,
            defaultVoiceId = defaultVoice.id,
            defaultLocale = defaultVoice.locale,
            supportedLocales = registration.voices.mapTo(mutableSetOf()) { it.locale },
            voiceIds = registration.voices.mapTo(mutableSetOf()) { it.id },
        )
    }
}
